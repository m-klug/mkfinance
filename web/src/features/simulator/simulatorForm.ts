import {
  FixedIncomeSimulationItemIndexer as Indexer,
  FixedIncomeSimulationItemType as InstrumentType,
  type FixedIncomeSimulationItem,
  type MarketRate,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

type IndexerValue = (typeof Indexer)[keyof typeof Indexer];
type InstrumentValue = (typeof InstrumentType)[keyof typeof InstrumentType];

export const INSTRUMENT_ORDER: InstrumentValue[] = [
  InstrumentType.CDB,
  InstrumentType.LC,
  InstrumentType.LCI,
  InstrumentType.LCA,
  InstrumentType.TESOURO_SELIC,
  InstrumentType.TESOURO_PREFIXADO,
  InstrumentType.TESOURO_IPCA,
];

// Mirrors FixedIncomeParams.indexerFits on the API: what each instrument is sold with.
const INSTRUMENT_INDEXERS: Record<InstrumentValue, IndexerValue[]> = {
  [InstrumentType.CDB]: [Indexer.CDI_PERCENT, Indexer.PREFIXED, Indexer.IPCA_PLUS],
  [InstrumentType.LC]: [Indexer.CDI_PERCENT, Indexer.PREFIXED, Indexer.IPCA_PLUS],
  [InstrumentType.LCI]: [Indexer.CDI_PERCENT, Indexer.PREFIXED, Indexer.IPCA_PLUS],
  [InstrumentType.LCA]: [Indexer.CDI_PERCENT, Indexer.PREFIXED, Indexer.IPCA_PLUS],
  [InstrumentType.TESOURO_SELIC]: [Indexer.SELIC_PERCENT],
  [InstrumentType.TESOURO_PREFIXADO]: [Indexer.PREFIXED],
  [InstrumentType.TESOURO_IPCA]: [Indexer.IPCA_PLUS],
};

export const MAX_MONTHS = 600;

export type Premise = "cdi" | "selic" | "ipca";

/** What the user typed. Numbers that are free text stay strings until they are validated. */
export interface SimulationFormState {
  type: InstrumentValue;
  indexer: IndexerValue;
  /** % a.a. or % of the CDI/Selic, depending on the indexer. */
  rate: string;
  initialCents: number;
  monthlyCents: number;
  months: string;
  /** Empty means "use the market value". */
  cdi: string;
  selic: string;
  ipca: string;
}

export const DEFAULT_FORM: SimulationFormState = {
  type: InstrumentType.CDB,
  indexer: Indexer.CDI_PERCENT,
  rate: "",
  initialCents: 0,
  monthlyCents: 0,
  months: "12",
  cdi: "",
  selic: "",
  ipca: "",
};

export function indexersFor(type: InstrumentValue): IndexerValue[] {
  return INSTRUMENT_INDEXERS[type];
}

/** Switching instrument keeps the indexer when the new one is sold with it, else takes its first. */
export function withType(state: SimulationFormState, type: InstrumentValue): SimulationFormState {
  const allowed = indexersFor(type);
  return { ...state, type, indexer: allowed.includes(state.indexer) ? state.indexer : allowed[0] };
}

/** Which premise the indexer needs; prefixed needs none. */
export function premiseOf(indexer: IndexerValue): Premise | null {
  switch (indexer) {
    case Indexer.CDI_PERCENT:
      return "cdi";
    case Indexer.SELIC_PERCENT:
      return "selic";
    case Indexer.IPCA_PLUS:
      return "ipca";
    default:
      return null;
  }
}

/** "12,5" and "12.5" both read as 12.5; empty is null; anything else is NaN. */
export function parseDecimal(raw: string): number | null {
  const text = raw.trim().replace(",", ".");
  if (text === "") return null;
  if (!/^\d+(\.\d+)?$/.test(text)) return Number.NaN;
  return Number(text);
}

export type FormField = "rate" | "amount" | "months" | "premise";
export type FormErrors = Partial<Record<FormField, string>>;

/** Error keys under `simulator.errors`; an empty object means the form can be sent. */
export function validateForm(state: SimulationFormState): FormErrors {
  const errors: FormErrors = {};

  const rate = parseDecimal(state.rate);
  if (rate === null) errors.rate = "rateRequired";
  else if (Number.isNaN(rate) || rate > 1000) errors.rate = "rateInvalid";

  const months = parseDecimal(state.months);
  if (months === null || Number.isNaN(months) || !Number.isInteger(months) || months < 1 || months > MAX_MONTHS) {
    errors.months = "monthsInvalid";
  }

  const hasMonthly = state.monthlyCents > 0 && months !== null && months >= 2;
  if (state.initialCents <= 0 && !hasMonthly) errors.amount = "amountRequired";

  const premise = premiseOf(state.indexer);
  if (premise) {
    const value = parseDecimal(state[premise]);
    if (value !== null && (Number.isNaN(value) || value > 1000)) errors.premise = "premiseInvalid";
  }
  return errors;
}

/** The request item for a valid form; premises left empty are omitted so the API fills them. */
export function toRequestItem(state: SimulationFormState): FixedIncomeSimulationItem {
  const item: FixedIncomeSimulationItem = {
    type: state.type,
    indexer: state.indexer,
    rate: parseDecimal(state.rate) ?? 0,
    months: parseDecimal(state.months) ?? 0,
  };
  if (state.initialCents > 0) item.initialCents = state.initialCents;
  if (state.monthlyCents > 0) item.monthlyContributionCents = state.monthlyCents;

  const premise = premiseOf(state.indexer);
  const given = premise ? parseDecimal(state[premise]) : null;
  if (given !== null) {
    if (premise === "cdi") item.cdiAnnualPercent = given;
    if (premise === "selic") item.selicAnnualPercent = given;
    if (premise === "ipca") item.ipcaAnnualPercent = given;
  }
  return item;
}

/** The market rate behind a premise, or undefined when the API did not return it. */
export function marketRateFor(rates: MarketRate[] | undefined, premise: Premise): MarketRate | undefined {
  return rates?.find((rate) => rate.code === premise.toUpperCase());
}
