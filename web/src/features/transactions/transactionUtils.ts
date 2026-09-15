import type { TFunction } from "i18next";

import type { SelectOption } from "@/components/form/fields";
import {
  TransactionType,
  TransferDirection,
  type Account,
  type Category,
  type Transaction,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

/**
 * Business dates are plain local dates in America/Sao_Paulo (PLANEJAMENTO §5
 * R7) — never derived from a UTC timestamp, which is exactly how purchases end
 * up "jumping" a day and landing on the wrong statement.
 */
export function toIsoDate(date: Date): string {
  const month = `${date.getMonth() + 1}`.padStart(2, "0");
  const day = `${date.getDate()}`.padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

export function todayIso(): string {
  return toIsoDate(new Date());
}

export function firstDayOfCurrentMonthIso(): string {
  const now = new Date();
  return toIsoDate(new Date(now.getFullYear(), now.getMonth(), 1));
}

/**
 * The extrato's default window ends here, not on today: a competency is the whole month, and
 * anything already scheduled inside it — a recurrence occurrence, a bill entered with "agendar como
 * pendente" — is part of what the month looks like. Ending the window on today hid those rows even
 * though they existed, which also made the recurrence materialization (BE-8) pointless from the
 * extrato's side. Day 0 of the next month is the last day of this one.
 */
export function lastDayOfCurrentMonthIso(): string {
  const now = new Date();
  return toIsoDate(new Date(now.getFullYear(), now.getMonth() + 1, 0));
}

export function activeAccounts(accounts: Account[]): Account[] {
  return accounts.filter((account) => !account.archived);
}

export function categoryOptions(categories: Category[]): SelectOption[] {
  return [...categories]
    .sort((a, b) => a.name.localeCompare(b.name, "pt-BR"))
    .map((category) => ({ value: category.id, label: category.name }));
}

export function transactionTypeOptions(t: TFunction): SelectOption[] {
  return [
    { value: TransactionType.INCOME, label: t("transactionType.INCOME") },
    { value: TransactionType.EXPENSE, label: t("transactionType.EXPENSE") },
    { value: TransactionType.TRANSFER, label: t("transactionType.TRANSFER") },
  ];
}

/**
 * Signed amount for display: expenses and outgoing transfer legs are negative.
 *
 * <p>A transfer is a mirrored pair (R4) — the OUT leg leaves the source account, the IN leg lands on
 * the destination — so the sign has to come from {@code transferDirection}, never from the type
 * alone. Signing both legs negative made an aporte read as a deduction on the very account that
 * received the money.
 */
export function signedAmountCents(transaction: Transaction): number {
  if (transaction.type === TransactionType.TRANSFER) {
    return transaction.transferDirection === TransferDirection.OUT
      ? -transaction.amountCents
      : transaction.amountCents;
  }
  return transaction.type === TransactionType.INCOME
    ? transaction.amountCents
    : -transaction.amountCents;
}

/**
 * "2026-07-10" → "2026-07". Sliced, never parsed through `Date`, for the same
 * day-jump reasons as {@link toIsoDate} — used to look up the pillar
 * allocation report behind the quick-entry pillar-impact preview.
 */
export function monthOf(dateIso: string): string {
  return dateIso.slice(0, 7);
}

/** Shared field styling for the reskinned form inputs (design_handoff_mk_finance/README.md
 * §Design Tokens — field radius/background, 11/700 uppercase labels). Passed as
 * `fieldClassName`/`className` to the shared TextField/SelectField primitives so
 * their DOM (labels, roles) stays exactly as before for tests. */
export const FORM_FIELD_CLASSNAME =
  "[&_label]:text-[11px] [&_label]:font-bold [&_label]:uppercase [&_label]:tracking-wide [&_label]:text-ink-label " +
  "[&_input]:h-11 [&_input]:rounded-field [&_input]:border-none [&_input]:bg-field [&_input]:px-3.5 [&_input]:text-[13px] [&_input]:font-semibold [&_input]:shadow-none [&_input]:text-ink-primary " +
  "[&_select]:h-11 [&_select]:rounded-field [&_select]:border-none [&_select]:bg-field [&_select]:px-3.5 [&_select]:text-[13px] [&_select]:font-semibold [&_select]:shadow-none [&_select]:text-ink-primary";

export function lookupName<T extends { id: string; name: string }>(
  items: T[],
  id: string | null | undefined,
): string | undefined {
  if (!id) return undefined;
  return items.find((item) => item.id === id)?.name;
}
