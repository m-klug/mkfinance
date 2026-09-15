import type { Card, Statement } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { StatementStatus } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { toIsoDate } from "@/features/transactions/transactionUtils";

// Statement/day math shared by the cards screens. Business dates are plain
// local dates in America/Sao_Paulo (PLANEJAMENTO §5 R7): a statement never
// "jumps" a day because a UTC timestamp was involved.

/** Closing/due day is 1..28 (the contract cap avoids month-length ambiguity). */
export const STATEMENT_DAYS: number[] = Array.from({ length: 28 }, (_, i) => i + 1);

/** Installment count offered on quick card entry — the common range; the API allows up to 60. */
export const INSTALLMENT_COUNTS: number[] = Array.from({ length: 12 }, (_, i) => i + 1);

/**
 * Available limit = limit − unpaid statements (the backend already computes it,
 * R1). Falls back to the full limit only while the field is absent.
 */
export function availableLimitCents(card: Card): number {
  return card.availableLimitCents ?? card.limitCents;
}

/** Amount still owed on a statement — status is derived from this, never stored. */
export function remainingCents(statement: Pick<Statement, "totalCents" | "paidCents">): number {
  return Math.max(0, statement.totalCents - statement.paidCents);
}

/**
 * Splits a total into `count` installments with the cent remainder landing on
 * the last one (R1): 10000/3 → [3333, 3333, 3334]. This mirrors the backend
 * InstallmentSplitter so the quick-entry preview matches what gets charged.
 */
export function splitInstallments(totalCents: number, count: number): number[] {
  if (count <= 1) return [totalCents];
  const base = Math.floor(totalCents / count);
  const parts = Array.from({ length: count }, () => base);
  parts[count - 1] = totalCents - base * (count - 1);
  return parts;
}

export const CURRENT_MONTH = (): string => monthIso(new Date());

function monthIso(date: Date): string {
  return `${date.getFullYear()}-${`${date.getMonth() + 1}`.padStart(2, "0")}`;
}

/** Shifts a YYYY-MM reference month by whole months, staying on the first day. */
export function addMonths(isoMonth: string, delta: number): string {
  const [year, month] = isoMonth.split("-").map(Number);
  return monthIso(new Date(year, month - 1 + delta, 1));
}

/** Whole days from today (local) to an ISO date; negative when already past. */
export function daysUntil(isoDate: string): number {
  const [year, month, day] = isoDate.split("-").map(Number);
  const due = new Date(year, month - 1, day).getTime();
  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  return Math.round((due - today) / 86_400_000);
}

/** A bill worth flagging: still owed and due within a week (or already overdue). */
export function isDueSoon(statement: Statement): boolean {
  return remainingCents(statement) > 0 && daysUntil(statement.dueDate) <= 7;
}

export function todayIsoDate(): string {
  return toIsoDate(new Date());
}

/** Statements that still owe money can take a payment, whatever their status. */
export function isPayable(statement: Pick<Statement, "totalCents" | "paidCents">): boolean {
  return remainingCents(statement) > 0;
}

/** Share of the limit already used, 0–100 — display only, for the usage bar. */
export function usagePercent(card: Card): number {
  if (card.limitCents <= 0) return 0;
  const used = card.limitCents - availableLimitCents(card);
  return Math.min(100, Math.max(0, (used / card.limitCents) * 100));
}

export { StatementStatus };
