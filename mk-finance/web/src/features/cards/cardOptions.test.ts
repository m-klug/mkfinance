import { afterEach, describe, expect, it, vi } from "vitest";

import {
  addMonths,
  availableLimitCents,
  isDueSoon,
  remainingCents,
  splitInstallments,
} from "@/features/cards/cardOptions";
import { StatementStatus, type Card, type Statement } from "@/lib/api/generated/mKFinanceAPI.schemas";

describe("splitInstallments", () => {
  it("puts the cent remainder on the last installment (R1)", () => {
    // 100,00 in 3× is the canonical scenario: 33,33 + 33,33 + 33,34.
    expect(splitInstallments(10000, 3)).toEqual([3333, 3333, 3334]);
  });

  it("splits evenly when there is no remainder", () => {
    expect(splitInstallments(9999, 3)).toEqual([3333, 3333, 3333]);
  });

  it("always keeps the whole total, whatever the remainder", () => {
    for (const total of [10001, 12345, 99, 100000]) {
      for (const count of [1, 2, 3, 4, 7, 12]) {
        const parts = splitInstallments(total, count);
        expect(parts).toHaveLength(count);
        expect(parts.reduce((sum, part) => sum + part, 0)).toBe(total);
        // The remainder rides the last one, so it is the only one that may differ.
        expect(parts.slice(0, -1).every((part) => part === parts[0])).toBe(true);
      }
    }
  });

  it("returns the full amount for a single installment", () => {
    expect(splitInstallments(4599, 1)).toEqual([4599]);
  });
});

describe("statement math", () => {
  it("computes the remaining balance, floored at zero", () => {
    expect(remainingCents({ totalCents: 10000, paidCents: 3000 })).toBe(7000);
    expect(remainingCents({ totalCents: 10000, paidCents: 10000 })).toBe(0);
  });

  it("falls back to the full limit only when the available one is absent", () => {
    expect(availableLimitCents({ limitCents: 500000, availableLimitCents: 350000 } as Card)).toBe(
      350000,
    );
    expect(availableLimitCents({ limitCents: 500000 } as Card)).toBe(500000);
  });

  it("shifts reference months across year boundaries", () => {
    expect(addMonths("2026-07", 1)).toBe("2026-08");
    expect(addMonths("2026-12", 1)).toBe("2027-01");
    expect(addMonths("2026-01", -1)).toBe("2025-12");
  });
});

describe("isDueSoon", () => {
  afterEach(() => vi.useRealTimers());

  function statement(dueDate: string, paidCents = 0): Statement {
    return {
      id: "s",
      cardId: "c",
      referenceMonth: "2026-08",
      closingDate: "2026-07-20",
      dueDate,
      status: StatementStatus.OPEN,
      totalCents: 10000,
      paidCents,
    };
  }

  it("flags an unpaid bill due within a week", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 7, 1));
    expect(isDueSoon(statement("2026-08-05"))).toBe(true);
  });

  it("ignores a bill still far from its due date", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 7, 1));
    expect(isDueSoon(statement("2026-09-01"))).toBe(false);
  });

  it("never flags a fully paid bill", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(2026, 7, 1));
    expect(isDueSoon(statement("2026-08-05", 10000))).toBe(false);
  });
});
