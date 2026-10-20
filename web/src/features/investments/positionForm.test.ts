import { describe, expect, it } from "vitest";

import type { Position } from "@/lib/api/generated/mKFinanceAPI.schemas";
import {
  DEFAULT_POSITION_FORM,
  POSITION_INSTRUMENTS,
  daysBetween,
  formFromPosition,
  positionIndexersFor,
  toCreateRequest,
  toUpdateRequest,
  validatePositionForm,
  withPositionType,
  type PositionFormState,
} from "@/features/investments/positionForm";

const TODAY = "2026-10-20";

const linked: PositionFormState = {
  ...DEFAULT_POSITION_FORM,
  origin: "CONTRIBUTION",
  contributionId: "leg-1",
  vaultId: "vault-1",
  rate: "110",
  maturityDate: "2027-10-20",
};

const manual: PositionFormState = {
  ...DEFAULT_POSITION_FORM,
  origin: "MANUAL",
  accountId: "acc-1",
  principalCents: 800_000,
  purchaseDate: "2026-03-10",
  rate: "12,5",
  maturityDate: "2027-03-10",
};

describe("instruments and indexers", () => {
  it("offers no IPCA-linked title and no IPCA indexer", () => {
    expect(POSITION_INSTRUMENTS).not.toContain("TESOURO_IPCA");
    for (const type of POSITION_INSTRUMENTS) {
      expect(positionIndexersFor(type)).not.toContain("IPCA_PLUS");
      expect(positionIndexersFor(type).length).toBeGreaterThan(0);
    }
  });

  it("a CDB can be fixed or a percent of the CDI; the Tesouro Selic only follows the Selic", () => {
    expect(positionIndexersFor("CDB")).toEqual(["CDI_PERCENT", "PREFIXED"]);
    expect(positionIndexersFor("TESOURO_SELIC")).toEqual(["SELIC_PERCENT"]);
  });

  it("switching instrument keeps a compatible indexer and otherwise takes the first", () => {
    expect(withPositionType({ ...DEFAULT_POSITION_FORM, indexer: "PREFIXED" }, "LCI").indexer).toBe("PREFIXED");
    expect(withPositionType(DEFAULT_POSITION_FORM, "TESOURO_SELIC").indexer).toBe("SELIC_PERCENT");
    expect(withPositionType(DEFAULT_POSITION_FORM, "TESOURO_PREFIXADO").indexer).toBe("PREFIXED");
  });
});

describe("validatePositionForm", () => {
  it("a complete linked form has no errors", () => {
    expect(validatePositionForm(linked, "create", TODAY, "2026-07-01")).toEqual({});
  });

  it("a linked form needs the purchase chosen", () => {
    expect(validatePositionForm({ ...linked, contributionId: "" }, "create", TODAY).contribution).toBe("contributionRequired");
  });

  it("the rate is required and numeric", () => {
    expect(validatePositionForm({ ...linked, rate: "" }, "create", TODAY, "2026-07-01").rate).toBe("rateRequired");
    expect(validatePositionForm({ ...linked, rate: "abc" }, "create", TODAY, "2026-07-01").rate).toBe("rateInvalid");
  });

  it("the maturity is required and must come after the purchase's date", () => {
    expect(validatePositionForm({ ...linked, maturityDate: "" }, "create", TODAY, "2026-07-01").maturityDate).toBe("maturityRequired");
    expect(validatePositionForm({ ...linked, maturityDate: "2026-07-01" }, "create", TODAY, "2026-07-01").maturityDate).toBe("maturityBeforePurchase");
  });

  it("a complete manual form has no errors", () => {
    expect(validatePositionForm(manual, "create", TODAY)).toEqual({});
  });

  it("a manual form needs account, amount and purchase date", () => {
    const errors = validatePositionForm({ ...manual, accountId: "", principalCents: 0, purchaseDate: "" }, "create", TODAY);
    expect(errors.account).toBe("accountRequired");
    expect(errors.principal).toBe("principalRequired");
    expect(errors.purchaseDate).toBe("purchaseDateRequired");
  });

  it("a purchase cannot be in the future, and the maturity must follow the manual purchase date", () => {
    expect(validatePositionForm({ ...manual, purchaseDate: "2026-12-01" }, "create", TODAY).purchaseDate).toBe("purchaseDateFuture");
    expect(validatePositionForm({ ...manual, maturityDate: "2026-03-10" }, "create", TODAY).maturityDate).toBe("maturityBeforePurchase");
  });

  it("editing a linked position does not ask for the purchase again, and checks the maturity against its date", () => {
    const editing = { ...linked, contributionId: "leg-1" };
    expect(validatePositionForm(editing, "edit", TODAY, "2026-07-01")).toEqual({});
    expect(validatePositionForm({ ...editing, maturityDate: "2026-06-01" }, "edit", TODAY, "2026-07-01").maturityDate).toBe("maturityBeforePurchase");
  });

  it("editing a manual position does not ask for the account", () => {
    expect(validatePositionForm({ ...manual, accountId: "" }, "edit", TODAY).account).toBeUndefined();
  });
});

describe("requests", () => {
  it("a linked create sends the purchase and the vault, and none of the manual fields", () => {
    expect(toCreateRequest({ ...linked, name: " CDB Inter " })).toEqual({
      type: "CDB",
      indexer: "CDI_PERCENT",
      rate: 110,
      maturityDate: "2027-10-20",
      name: "CDB Inter",
      vaultId: "vault-1",
      contributionTransactionId: "leg-1",
    });
  });

  it("a manual create sends account, amount and purchase date, and no purchase", () => {
    const request = toCreateRequest(manual);
    expect(request).toMatchObject({ accountId: "acc-1", principalCents: 800_000, purchaseDate: "2026-03-10", rate: 12.5 });
    expect(request.contributionTransactionId).toBeUndefined();
    expect(request.vaultId).toBeUndefined();
    expect(request.name).toBeUndefined();
  });

  it("an update of a linked position leaves amount and date out", () => {
    const request = toUpdateRequest(linked);
    expect(request.principalCents).toBeUndefined();
    expect(request.purchaseDate).toBeUndefined();
    expect(request.vaultId).toBe("vault-1");
  });

  it("an update of a manual position carries amount and date, and an empty vault is omitted so it clears", () => {
    const request = toUpdateRequest({ ...manual, vaultId: "" });
    expect(request.principalCents).toBe(800_000);
    expect(request.purchaseDate).toBe("2026-03-10");
    expect(request.vaultId).toBeUndefined();
  });
});

describe("formFromPosition", () => {
  const position = {
    id: "p1",
    origin: "MANUAL",
    accountId: "acc-1",
    accountName: "Corretora",
    vaultId: null,
    name: null,
    type: "LCI",
    indexer: "PREFIXED",
    rate: 12.5,
    principalCents: 500_000,
    purchaseDate: "2026-03-10",
    maturityDate: "2027-03-10",
    status: "ACTIVE",
    awaitingPayment: false,
  } as Position;

  it("turns a position back into the form, with a comma for the decimal", () => {
    const form = formFromPosition(position);
    expect(form).toMatchObject({ origin: "MANUAL", accountId: "acc-1", vaultId: "", name: "", rate: "12,5", principalCents: 500_000 });
  });
});

describe("daysBetween", () => {
  it("counts whole days, negative once past", () => {
    expect(daysBetween("2026-10-20", "2026-10-30")).toBe(10);
    expect(daysBetween("2026-10-20", "2026-10-20")).toBe(0);
    expect(daysBetween("2026-10-20", "2026-10-10")).toBe(-10);
    expect(daysBetween("2026-12-30", "2027-01-02")).toBe(3);
  });
});
