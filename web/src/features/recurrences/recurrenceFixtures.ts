import { http, HttpResponse } from "msw";

import {
  RecurrenceFrequency,
  RecurrenceType,
  type Recurrence,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import { checkingAccount, groceriesCategory, salaryCategory } from "@/test/financeFixtures";

// The orval MSW mock answers with random faker after a 1s delay — useless for
// assertions (CONTEXTO §7.5). These fixtures + handlers give the recurrences
// tests a small, stable series to work against.

export const rentRecurrence: Recurrence = {
  id: "rec-rent",
  type: RecurrenceType.EXPENSE,
  accountId: checkingAccount.id,
  categoryId: groceriesCategory.id,
  amountCents: 150000,
  description: "Aluguel",
  note: null,
  frequency: RecurrenceFrequency.MONTHLY,
  dayOfMonth: 5,
  dayOfWeek: null,
  monthOfYear: null,
  startDate: "2026-01-05",
  endDate: null,
  active: true,
};

// startDate in the future — never materialized a single occurrence, so the
// scope dialog must not appear for it (recurrenceOptions.mayHaveMaterialized).
export const futureSalaryRecurrence: Recurrence = {
  id: "rec-future-salary",
  type: RecurrenceType.INCOME,
  accountId: checkingAccount.id,
  categoryId: salaryCategory.id,
  amountCents: 500000,
  description: "Salário mensal",
  note: null,
  frequency: RecurrenceFrequency.MONTHLY,
  dayOfMonth: 1,
  dayOfWeek: null,
  monthOfYear: null,
  startDate: "2099-01-01",
  endDate: null,
  active: true,
};

export const closedGymRecurrence: Recurrence = {
  id: "rec-gym-closed",
  type: RecurrenceType.EXPENSE,
  accountId: checkingAccount.id,
  categoryId: groceriesCategory.id,
  amountCents: 9990,
  description: "Academia",
  note: null,
  frequency: RecurrenceFrequency.MONTHLY,
  dayOfMonth: 10,
  dayOfWeek: null,
  monthOfYear: null,
  startDate: "2025-01-10",
  endDate: "2026-05-09",
  active: false,
};

export const defaultRecurrences: Recurrence[] = [rentRecurrence, futureSalaryRecurrence];

interface RecurrenceHandlerOptions {
  recurrences?: Recurrence[];
  /** Every recurrence, including the closed ones — served when ?active is omitted. */
  allRecurrences?: Recurrence[];
}

export function recurrenceReadHandlers({
  recurrences = defaultRecurrences,
  allRecurrences = [...defaultRecurrences, closedGymRecurrence],
}: RecurrenceHandlerOptions = {}) {
  return [
    http.get("*/workspaces/:workspaceId/recurrences", ({ request }) => {
      const active = new URL(request.url).searchParams.get("active");
      if (active === "true") return HttpResponse.json(recurrences.filter((r) => r.active));
      if (active === "false") return HttpResponse.json(recurrences.filter((r) => !r.active));
      return HttpResponse.json(allRecurrences);
    }),
  ];
}
