import type { TFunction } from "i18next";

import type { SelectOption } from "@/components/form/fields";
import {
  CategoryType,
  DayOfWeek,
  RecurrenceFrequency,
  RecurrenceRequestType,
  type Account,
  type Category,
  type Recurrence,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

export const FREQUENCIES: RecurrenceFrequency[] = [
  RecurrenceFrequency.MONTHLY,
  RecurrenceFrequency.WEEKLY,
  RecurrenceFrequency.YEARLY,
];

export const DAYS_OF_WEEK: DayOfWeek[] = [
  DayOfWeek.MONDAY,
  DayOfWeek.TUESDAY,
  DayOfWeek.WEDNESDAY,
  DayOfWeek.THURSDAY,
  DayOfWeek.FRIDAY,
  DayOfWeek.SATURDAY,
  DayOfWeek.SUNDAY,
];

function toIsoDateLocal(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

/** Business dates are plain local dates (PLANEJAMENTO §5 R7) — never UTC. */
export function todayIso(): string {
  return toIsoDateLocal(new Date());
}

// RecurrenceRequestType and TransactionType share the same INCOME/EXPENSE
// strings, so the existing transactionType.* labels are reused instead of
// duplicating a two-entry map.
export function recurrenceTypeOptions(t: TFunction): SelectOption[] {
  return [
    { value: RecurrenceRequestType.INCOME, label: t("transactionType.INCOME") },
    { value: RecurrenceRequestType.EXPENSE, label: t("transactionType.EXPENSE") },
  ];
}

export function frequencyOptions(t: TFunction): SelectOption[] {
  return FREQUENCIES.map((frequency) => ({
    value: frequency,
    label: t(`recurrenceFrequency.${frequency}`),
  }));
}

export function dayOfWeekOptions(t: TFunction): SelectOption[] {
  return DAYS_OF_WEEK.map((day) => ({ value: day, label: t(`dayOfWeek.${day}`) }));
}

export function accountOptions(accounts: Account[]): SelectOption[] {
  return accounts
    .filter((account) => !account.archived)
    .map((account) => ({ value: account.id, label: account.name }));
}

/** Categories of a recurrence's type, mirroring the extrato's own filter (system categories excluded). */
export function categoryOptionsForType(categories: Category[], type: string): SelectOption[] {
  const wanted = type === RecurrenceRequestType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
  return [...categories]
    .filter((category) => category.type === wanted && !category.system)
    .sort((a, b) => a.name.localeCompare(b.name, "pt-BR"))
    .map((category) => ({ value: category.id, label: category.name }));
}

export function lookupName<T extends { id: string; name: string }>(
  items: T[],
  id: string | null | undefined,
): string | undefined {
  if (!id) return undefined;
  return items.find((item) => item.id === id)?.name;
}

/**
 * "2026" + monthOfYear (1-12) → the localized month name, for the schedule
 * summary of a YEARLY rule. The year is irrelevant, only Intl's month table is used.
 */
function monthName(monthOfYear: number): string {
  return new Date(2000, monthOfYear - 1, 1).toLocaleDateString("pt-BR", { month: "long" });
}

/** Human-readable summary of when a rule fires, for the list. */
export function scheduleLabel(recurrence: Recurrence, t: TFunction): string {
  switch (recurrence.frequency) {
    case RecurrenceFrequency.WEEKLY:
      return t("recurrences.schedule.weekly", {
        day: recurrence.dayOfWeek ? t(`dayOfWeek.${recurrence.dayOfWeek}`) : "",
      });
    case RecurrenceFrequency.YEARLY:
      return t("recurrences.schedule.yearly", {
        day: recurrence.dayOfMonth ?? "",
        month: recurrence.monthOfYear ? monthName(recurrence.monthOfYear) : "",
      });
    case RecurrenceFrequency.MONTHLY:
    default:
      return t("recurrences.schedule.monthly", { day: recurrence.dayOfMonth ?? "" });
  }
}

/**
 * The API exposes no "has this rule ever materialized an occurrence" flag —
 * occurrences only exist implicitly, lazily, per month read. A rule whose
 * startDate has not arrived yet certainly never produced one, so the scope
 * dialog (THIS_ONE / THIS_AND_FUTURE) is skipped for it; a rule that started
 * in the past or today might have, so we ask. This slightly over-asks (a
 * past-started rule whose month was never opened has nothing to scope
 * either) but never under-asks, which is the safe direction.
 */
export function mayHaveMaterialized(recurrence: Recurrence, today: string = todayIso()): boolean {
  return recurrence.startDate <= today;
}

/** Generic field-level message for the codes RecurrenceRuleService / bean validation emit. */
export function translateFieldError(t: TFunction, code: string | undefined): string {
  switch (code) {
    case "REQUIRED":
    case "NOT_NULL":
    case "NOT_BLANK":
      return t("validation.required");
    case "MIN":
    case "POSITIVE":
      return t("validation.amountPositive");
    default:
      return t("validation.invalid");
  }
}
