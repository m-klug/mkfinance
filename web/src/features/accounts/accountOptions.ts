import type { TFunction } from "i18next";

import {
  AccountType,
  ContributionPillar,
  type Account,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import type { SelectOption } from "@/components/form/fields";

// Account types in the order they appear in PLANEJAMENTO §6.2.
export const ACCOUNT_TYPES: AccountType[] = [
  AccountType.CHECKING,
  AccountType.SAVINGS,
  AccountType.CASH,
  AccountType.INVESTMENT,
  AccountType.OTHER,
];

// Only these two pillars can receive a contribution (R9).
export const CONTRIBUTION_PILLARS: ContributionPillar[] = [
  ContributionPillar.GOALS,
  ContributionPillar.FINANCIAL_FREEDOM,
];

/** Accounts that hold savings/investments — destination of a contribution (R9). */
export function isContributionAccount(account: Pick<Account, "type">): boolean {
  return account.type === AccountType.INVESTMENT || account.type === AccountType.SAVINGS;
}

/**
 * BE-3 fills `balanceCents`; until then the contract returns null and the best
 * available truth is the opening balance (R5: balance = initial + settled).
 */
export function effectiveBalanceCents(account: Account): number {
  return account.balanceCents ?? account.initialBalanceCents;
}

export function projectedBalanceCents(account: Account): number {
  return account.projectedBalanceCents ?? effectiveBalanceCents(account);
}

export function accountTypeOptions(t: TFunction): SelectOption[] {
  return ACCOUNT_TYPES.map((type) => ({ value: type, label: t(`accountType.${type}`) }));
}

export function contributionPillarOptions(t: TFunction): SelectOption[] {
  return CONTRIBUTION_PILLARS.map((pillar) => ({
    value: pillar,
    label: t(`pillar.${pillar}`),
  }));
}

export function accountOptions(accounts: Account[]): SelectOption[] {
  return accounts.map((account) => ({ value: account.id, label: account.name }));
}
