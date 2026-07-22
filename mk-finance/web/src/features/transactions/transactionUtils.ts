import type { TFunction } from "i18next";

import type { SelectOption } from "@/components/form/fields";
import {
  TransactionType,
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

/** Signed amount for display: expenses and outgoing transfer legs are negative. */
export function signedAmountCents(transaction: Transaction): number {
  return transaction.type === TransactionType.INCOME
    ? transaction.amountCents
    : -transaction.amountCents;
}

export function lookupName<T extends { id: string; name: string }>(
  items: T[],
  id: string | null | undefined,
): string | undefined {
  if (!id) return undefined;
  return items.find((item) => item.id === id)?.name;
}
