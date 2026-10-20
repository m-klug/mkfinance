import { describe, expect, it } from "vitest";

import {
  FixedIncomeSimulationItemIndexer as Indexer,
  FixedIncomeSimulationItemType as InstrumentType,
  type MarketRate,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import {
  DEFAULT_FORM,
  indexersFor,
  marketRateFor,
  parseDecimal,
  premiseOf,
  toRequestItem,
  validateForm,
  withType,
  type SimulationFormState,
} from "@/features/simulator/simulatorForm";

const valid: SimulationFormState = { ...DEFAULT_FORM, rate: "110", initialCents: 1_000_000 };

describe("parseDecimal", () => {
  it("reads a comma or a dot as the decimal separator", () => {
    expect(parseDecimal("12,5")).toBe(12.5);
    expect(parseDecimal("12.5")).toBe(12.5);
    expect(parseDecimal(" 110 ")).toBe(110);
  });

  it("empty is null, anything else that is not a number is NaN", () => {
    expect(parseDecimal("")).toBeNull();
    expect(parseDecimal("   ")).toBeNull();
    expect(parseDecimal("abc")).toBeNaN();
    expect(parseDecimal("12,5,3")).toBeNaN();
    expect(parseDecimal("-3")).toBeNaN();
  });
});

describe("instrument and indexer", () => {
  it("each instrument offers only the indexers it is sold with", () => {
    expect(indexersFor(InstrumentType.CDB)).toEqual([Indexer.CDI_PERCENT, Indexer.PREFIXED, Indexer.IPCA_PLUS]);
    expect(indexersFor(InstrumentType.TESOURO_SELIC)).toEqual([Indexer.SELIC_PERCENT]);
    expect(indexersFor(InstrumentType.TESOURO_PREFIXADO)).toEqual([Indexer.PREFIXED]);
    expect(indexersFor(InstrumentType.TESOURO_IPCA)).toEqual([Indexer.IPCA_PLUS]);
  });

  it("switching instrument keeps the indexer when the new one allows it", () => {
    const prefixed = { ...DEFAULT_FORM, indexer: Indexer.PREFIXED };
    expect(withType(prefixed, InstrumentType.LCI).indexer).toBe(Indexer.PREFIXED);
  });

  it("switching to an instrument that does not allow the indexer takes its first one", () => {
    expect(withType(DEFAULT_FORM, InstrumentType.TESOURO_SELIC).indexer).toBe(Indexer.SELIC_PERCENT);
    expect(withType(DEFAULT_FORM, InstrumentType.TESOURO_PREFIXADO).indexer).toBe(Indexer.PREFIXED);
  });

  it("each indexer maps to the premise it needs; prefixed needs none", () => {
    expect(premiseOf(Indexer.CDI_PERCENT)).toBe("cdi");
    expect(premiseOf(Indexer.SELIC_PERCENT)).toBe("selic");
    expect(premiseOf(Indexer.IPCA_PLUS)).toBe("ipca");
    expect(premiseOf(Indexer.PREFIXED)).toBeNull();
  });
});

describe("validateForm", () => {
  it("a complete form has no errors", () => {
    expect(validateForm(valid)).toEqual({});
  });

  it("the rate is required and bounded", () => {
    expect(validateForm({ ...valid, rate: "" }).rate).toBe("rateRequired");
    expect(validateForm({ ...valid, rate: "abc" }).rate).toBe("rateInvalid");
    expect(validateForm({ ...valid, rate: "1001" }).rate).toBe("rateInvalid");
  });

  it("the term is a whole number of months from 1 to 600", () => {
    for (const months of ["", "0", "601", "1,5", "x"]) {
      expect(validateForm({ ...valid, months }).months).toBe("monthsInvalid");
    }
    expect(validateForm({ ...valid, months: "600" }).months).toBeUndefined();
  });

  it("something must be applied: an initial amount, or a monthly one with at least 2 months", () => {
    expect(validateForm({ ...valid, initialCents: 0 }).amount).toBe("amountRequired");
    expect(validateForm({ ...valid, initialCents: 0, monthlyCents: 50_000, months: "1" }).amount).toBe("amountRequired");
    expect(validateForm({ ...valid, initialCents: 0, monthlyCents: 50_000, months: "2" }).amount).toBeUndefined();
  });

  it("a premise left empty is fine, one typed wrong is not", () => {
    expect(validateForm({ ...valid, cdi: "" }).premise).toBeUndefined();
    expect(validateForm({ ...valid, cdi: "13,65" }).premise).toBeUndefined();
    expect(validateForm({ ...valid, cdi: "abc" }).premise).toBe("premiseInvalid");
  });

  it("only the premise of the chosen indexer is checked", () => {
    const prefixed = { ...valid, indexer: Indexer.PREFIXED, cdi: "abc" };
    expect(validateForm(prefixed).premise).toBeUndefined();
  });
});

describe("toRequestItem", () => {
  it("builds the item and leaves out the zero amounts and the empty premise", () => {
    expect(toRequestItem(valid)).toEqual({
      type: InstrumentType.CDB,
      indexer: Indexer.CDI_PERCENT,
      rate: 110,
      months: 12,
      initialCents: 1_000_000,
    });
  });

  it("sends the monthly contribution when there is one", () => {
    expect(toRequestItem({ ...valid, monthlyCents: 50_000 }).monthlyContributionCents).toBe(50_000);
  });

  it("sends the premise of the indexer when the user typed it", () => {
    expect(toRequestItem({ ...valid, cdi: "13,65" }).cdiAnnualPercent).toBe(13.65);
    const selic = { ...valid, type: InstrumentType.TESOURO_SELIC, indexer: Indexer.SELIC_PERCENT, selic: "14" };
    expect(toRequestItem(selic).selicAnnualPercent).toBe(14);
    const ipca = { ...valid, indexer: Indexer.IPCA_PLUS, ipca: "4,5" };
    expect(toRequestItem(ipca).ipcaAnnualPercent).toBe(4.5);
  });

  it("never sends a premise the indexer does not use, even if one was typed before switching", () => {
    const item = toRequestItem({ ...valid, indexer: Indexer.PREFIXED, cdi: "13,65" });
    expect(item.cdiAnnualPercent).toBeUndefined();
  });
});

describe("marketRateFor", () => {
  const rates: MarketRate[] = [
    { code: "CDI", ratePercent: 13.65, referenceDate: "2026-10-19", fetchedAt: "2026-10-20T10:00:00Z" },
    { code: "IPCA", ratePercent: 4.58, referenceDate: "2026-09-01", fetchedAt: "2026-10-20T10:00:00Z" },
  ];

  it("finds the rate behind a premise", () => {
    expect(marketRateFor(rates, "cdi")?.ratePercent).toBe(13.65);
    expect(marketRateFor(rates, "ipca")?.ratePercent).toBe(4.58);
  });

  it("is undefined when the API did not return it, or nothing loaded yet", () => {
    expect(marketRateFor(rates, "selic")).toBeUndefined();
    expect(marketRateFor(undefined, "cdi")).toBeUndefined();
  });
});
