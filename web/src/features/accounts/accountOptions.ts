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

// The pillars whose money is set aside before being spent are the ones an aporte can feed (R9).
export const CONTRIBUTION_PILLARS: ContributionPillar[] = [
  ContributionPillar.GOALS,
  ContributionPillar.FINANCIAL_FREEDOM,
  ContributionPillar.KNOWLEDGE,
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

/** Up to two uppercase initials for the account row's avatar chip (display only). */
export function accountInitials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return "";
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return `${words[0][0]}${words[words.length - 1][0]}`.toUpperCase();
}

// Rotating palette for the account avatar chip — purely decorative, cycling
// through the pillar palette so the table doesn't render every row in mint.
// CSS custom properties (not hex) so tones follow the active theme, same as
// PILLAR_COLORS in dashboardFormat.ts.
const AVATAR_TONES = [
  "var(--pillar-fixed-costs)",
  "var(--pillar-comfort)",
  "var(--pillar-goals)",
  "var(--pillar-pleasures)",
  "var(--pillar-financial-freedom)",
  "var(--pillar-knowledge)",
];

export function accountAvatarTone(index: number): string {
  return AVATAR_TONES[index % AVATAR_TONES.length];
}
