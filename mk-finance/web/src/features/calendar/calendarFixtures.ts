import { http, HttpResponse } from "msw";

import {
  CalendarEntryKind,
  TransferDirection,
  type CalendarEntry,
  type CalendarMonth,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

// Deterministic month agenda for the calendar tests — the orval MSW mock
// answers with random faker after a 1s delay (CONTEXTO §7.5), useless here.

export const MONTH = "2026-08";

export const statementDueEntry: CalendarEntry = {
  date: "2026-08-05",
  kind: CalendarEntryKind.STATEMENT_DUE,
  description: "Nubank",
  amountCents: 120000,
  direction: null,
  transactionId: null,
  statementId: "stmt-2026-07",
  cardId: "card-nubank",
  accountId: null,
  categoryId: null,
  recurrenceId: null,
};

export const rentPendingEntry: CalendarEntry = {
  date: "2026-08-05",
  kind: CalendarEntryKind.PENDING_TRANSACTION,
  description: "Aluguel",
  amountCents: 150000,
  direction: TransferDirection.OUT,
  transactionId: "tx-rent",
  statementId: null,
  cardId: null,
  accountId: "acc-checking",
  categoryId: "cat-groceries",
  recurrenceId: "rec-rent",
};

export const salaryPendingEntry: CalendarEntry = {
  date: "2026-08-01",
  kind: CalendarEntryKind.PENDING_TRANSACTION,
  description: "Salário",
  amountCents: 500000,
  direction: TransferDirection.IN,
  transactionId: "tx-salary",
  statementId: null,
  cardId: null,
  accountId: "acc-checking",
  categoryId: "cat-salary",
  recurrenceId: null,
};

export const defaultCalendarEntries: CalendarEntry[] = [
  salaryPendingEntry,
  rentPendingEntry,
  statementDueEntry,
];

interface CalendarHandlerOptions {
  month?: string;
  entries?: CalendarEntry[];
}

export function calendarReadHandlers({
  month = MONTH,
  entries = defaultCalendarEntries,
}: CalendarHandlerOptions = {}) {
  const body: CalendarMonth = { month, entries };
  return [http.get("*/workspaces/:workspaceId/calendar", () => HttpResponse.json(body))];
}
