import {
  FixedIncomeSimulationItemIndexer as Indexer,
  FixedIncomeSimulationItemType as InstrumentType,
  type Position,
  type PositionRequest,
  type PositionUpdateRequest,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import { indexersFor, parseDecimal } from "@/features/simulator/simulatorForm";

type InstrumentValue = (typeof InstrumentType)[keyof typeof InstrumentType];
type IndexerValue = (typeof Indexer)[keyof typeof Indexer];

/** The titles a position can be registered for; the IPCA-linked one is not valued yet. */
export const POSITION_INSTRUMENTS: InstrumentValue[] = [
  InstrumentType.CDB,
  InstrumentType.LC,
  InstrumentType.LCI,
  InstrumentType.LCA,
  InstrumentType.TESOURO_SELIC,
  InstrumentType.TESOURO_PREFIXADO,
];

/** What each instrument is sold with, minus the IPCA-linked indexer (not valued yet). */
export function positionIndexersFor(type: InstrumentValue): IndexerValue[] {
  return indexersFor(type).filter((indexer) => indexer !== Indexer.IPCA_PLUS);
}

export type PositionOrigin = "CONTRIBUTION" | "MANUAL";

/** What the user typed; money in cents, free-text numbers stay strings until validated. */
export interface PositionFormState {
  origin: PositionOrigin;
  /** Origin CONTRIBUTION: the chosen purchase (entering transfer leg). */
  contributionId: string;
  /** Origin MANUAL: the investment/savings account. */
  accountId: string;
  principalCents: number;
  /** Origin MANUAL: when it was bought. */
  purchaseDate: string;
  vaultId: string;
  name: string;
  type: InstrumentValue;
  indexer: IndexerValue;
  rate: string;
  maturityDate: string;
}

export const DEFAULT_POSITION_FORM: PositionFormState = {
  origin: "CONTRIBUTION",
  contributionId: "",
  accountId: "",
  principalCents: 0,
  purchaseDate: "",
  vaultId: "",
  name: "",
  type: InstrumentType.CDB,
  indexer: Indexer.CDI_PERCENT,
  rate: "",
  maturityDate: "",
};

/** Switching instrument keeps the indexer when the new one is sold with it, else takes its first. */
export function withPositionType(state: PositionFormState, type: InstrumentValue): PositionFormState {
  const allowed = positionIndexersFor(type);
  return { ...state, type, indexer: allowed.includes(state.indexer) ? state.indexer : allowed[0] };
}

/** The form of an existing position, for editing. */
export function formFromPosition(position: Position): PositionFormState {
  return {
    origin: position.origin,
    contributionId: position.contributionTransactionId ?? "",
    accountId: position.accountId,
    principalCents: position.principalCents,
    purchaseDate: position.purchaseDate,
    vaultId: position.vaultId ?? "",
    name: position.name ?? "",
    type: position.type as InstrumentValue,
    indexer: position.indexer as IndexerValue,
    rate: String(position.rate).replace(".", ","),
    maturityDate: position.maturityDate,
  };
}

export type PositionField = "contribution" | "account" | "principal" | "purchaseDate" | "rate" | "maturityDate";
export type PositionFormErrors = Partial<Record<PositionField, string>>;

/**
 * Error keys under `investments.errors`; empty means the form can be sent.
 *
 * @param purchaseDateOfContribution when a purchase transfer was chosen, its date (the maturity is
 *     checked against it); the manual form uses its own purchase date
 * @param today the current day, passed in so this stays pure
 */
export function validatePositionForm(
  state: PositionFormState,
  mode: "create" | "edit",
  today: string,
  purchaseDateOfContribution?: string,
): PositionFormErrors {
  const errors: PositionFormErrors = {};

  const rate = parseDecimal(state.rate);
  if (rate === null) errors.rate = "rateRequired";
  else if (Number.isNaN(rate) || rate > 1000) errors.rate = "rateInvalid";

  let purchaseDate = purchaseDateOfContribution ?? state.purchaseDate;
  if (mode === "create" && state.origin === "CONTRIBUTION") {
    if (!state.contributionId) errors.contribution = "contributionRequired";
  } else if (state.origin === "MANUAL") {
    if (mode === "create" && !state.accountId) errors.account = "accountRequired";
    if (state.principalCents <= 0) errors.principal = "principalRequired";
    if (!state.purchaseDate) errors.purchaseDate = "purchaseDateRequired";
    else if (state.purchaseDate > today) errors.purchaseDate = "purchaseDateFuture";
    purchaseDate = state.purchaseDate;
  }

  if (!state.maturityDate) errors.maturityDate = "maturityRequired";
  else if (purchaseDate && state.maturityDate <= purchaseDate) errors.maturityDate = "maturityBeforePurchase";
  return errors;
}

/** The create request for a valid form; an empty vault means "no caixinha". */
export function toCreateRequest(state: PositionFormState): PositionRequest {
  const request: PositionRequest = {
    type: state.type,
    indexer: state.indexer,
    rate: parseDecimal(state.rate) ?? 0,
    maturityDate: state.maturityDate,
  };
  if (state.name.trim()) request.name = state.name.trim();
  if (state.vaultId) request.vaultId = state.vaultId;
  if (state.origin === "CONTRIBUTION") {
    request.contributionTransactionId = state.contributionId;
  } else {
    request.accountId = state.accountId;
    request.principalCents = state.principalCents;
    request.purchaseDate = state.purchaseDate;
  }
  return request;
}

/** The update request: replaces the editable facts, so an empty vault clears it. */
export function toUpdateRequest(state: PositionFormState): PositionUpdateRequest {
  const request: PositionUpdateRequest = {
    type: state.type,
    indexer: state.indexer,
    rate: parseDecimal(state.rate) ?? 0,
    maturityDate: state.maturityDate,
  };
  if (state.name.trim()) request.name = state.name.trim();
  if (state.vaultId) request.vaultId = state.vaultId;
  if (state.origin === "MANUAL") {
    request.principalCents = state.principalCents;
    request.purchaseDate = state.purchaseDate;
  }
  return request;
}

/** Whole days from {@code today} to {@code date} (negative once past); both "YYYY-MM-DD". */
export function daysBetween(today: string, date: string): number {
  const [ty, tm, td] = today.split("-").map(Number);
  const [dy, dm, dd] = date.split("-").map(Number);
  return Math.round((Date.UTC(dy, dm - 1, dd) - Date.UTC(ty, tm - 1, td)) / 86_400_000);
}
