import type { TFunction } from "i18next";

import {
  TransactionStatus,
  TransactionType,
  type Transaction,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import { dateGroupLabel, groupTransactionsByDate } from "@/features/transactions/transactionUtils";

// Minimal stand-in for i18next's t(): the mobile Extrato's date-section
// header only needs the two literal keys below, translated identically to
// the real pt-BR strings so the "HOJE"/"ONTEM" assertions read naturally.
const t = ((key: string) => {
  if (key === "transactions.dateGroupToday") return "Hoje";
  if (key === "transactions.dateGroupYesterday") return "Ontem";
  return key;
}) as TFunction;

function transaction(overrides: Partial<Transaction> = {}): Transaction {
  return {
    id: "tx-1",
    accountId: "acc-1",
    categoryId: "cat-1",
    type: TransactionType.EXPENSE,
    amountCents: 1000,
    date: "2026-09-18",
    description: "Mercado",
    status: TransactionStatus.SETTLED,
    ...overrides,
  };
}

describe("dateGroupLabel", () => {
  it("labels today's date with the HOJE prefix (design_handoff mobile screen 02)", () => {
    expect(dateGroupLabel("2026-09-18", t, "2026-09-18")).toBe("HOJE · 18 SET");
  });

  it("labels yesterday's date with the ONTEM prefix", () => {
    expect(dateGroupLabel("2026-09-17", t, "2026-09-18")).toBe("ONTEM · 17 SET");
  });

  it("labels any other date with just the day and month, no prefix", () => {
    expect(dateGroupLabel("2026-09-10", t, "2026-09-18")).toBe("10 SET");
  });

  it("handles a month rollover for yesterday correctly", () => {
    expect(dateGroupLabel("2026-08-31", t, "2026-09-01")).toBe("ONTEM · 31 AGO");
  });
});

describe("groupTransactionsByDate", () => {
  it("buckets transactions by date and orders sections newest first", () => {
    const transactions = [
      transaction({ id: "tx-old", date: "2026-09-10" }),
      transaction({ id: "tx-today-1", date: "2026-09-18" }),
      transaction({ id: "tx-yesterday", date: "2026-09-17" }),
      transaction({ id: "tx-today-2", date: "2026-09-18" }),
    ];

    const groups = groupTransactionsByDate(transactions, t, "2026-09-18");

    expect(groups.map((group) => group.dateIso)).toEqual([
      "2026-09-18",
      "2026-09-17",
      "2026-09-10",
    ]);
    expect(groups[0].label).toBe("HOJE · 18 SET");
    expect(groups[0].transactions.map((tx) => tx.id)).toEqual(["tx-today-1", "tx-today-2"]);
  });

  it("returns an empty array for no transactions", () => {
    expect(groupTransactionsByDate([], t, "2026-09-18")).toEqual([]);
  });
});
