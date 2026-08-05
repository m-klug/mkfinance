import type { TFunction } from "i18next";

import type { SelectOption } from "@/components/form/fields";
import { formatCents } from "@/lib/format";
import {
  VaultGoalStatus,
  type Vault,
  type VaultComposition,
  type VaultGoal,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

// Pure, framework-free helpers for the vaults screen (co-located tests in
// vaultOptions.test.ts). The composition's numbers always come from the
// server (CT-2 decision 1) — nothing here re-derives a balance by summing
// vaults; it only reshapes numbers the API already computed for display.

export const DEFAULT_VAULT_COLOR = "#2563eb";
const UNALLOCATED_COLOR = "#94a3b8";

/** Vaults that can still take part in a reallocation or show up in the bar. */
export function activeVaults(vaults: Vault[]): Vault[] {
  return vaults.filter((vault) => !vault.archived);
}

export interface VaultBarSegment {
  key: string;
  label: string;
  cents: number;
  percent: number;
  color: string;
}

/**
 * One segment per vault with a positive settled balance, plus the unallocated
 * slice when it is itself positive. A negative unallocated slice is not part
 * of the bar (§4.3) — VaultCompositionCard renders it as a separate callout.
 */
export function vaultBarSegments(
  composition: Pick<VaultComposition, "vaults" | "balanceCents" | "unallocatedCents">,
  unallocatedLabel: string,
): VaultBarSegment[] {
  const total = Math.max(composition.balanceCents, 0);
  const parts: Array<Omit<VaultBarSegment, "percent">> = [];

  for (const vault of activeVaults(composition.vaults)) {
    if (vault.balanceCents > 0) {
      parts.push({
        key: vault.id,
        label: vault.name,
        cents: vault.balanceCents,
        color: vault.color ?? DEFAULT_VAULT_COLOR,
      });
    }
  }

  if (composition.unallocatedCents > 0) {
    parts.push({
      key: "unallocated",
      label: unallocatedLabel,
      cents: composition.unallocatedCents,
      color: UNALLOCATED_COLOR,
    });
  }

  if (total <= 0) {
    return parts.map((part) => ({ ...part, percent: 0 }));
  }
  return parts.map((part) => ({ ...part, percent: (part.cents / total) * 100 }));
}

/** §4.3: only the unallocated slice can go negative, and it is shown, not blocked. */
export function isUnallocatedOverdrawn(
  composition: Pick<VaultComposition, "unallocatedCents">,
): boolean {
  return composition.unallocatedCents < 0;
}

/** Clamped 0-100 width for a progress bar that must never overflow its track. */
export function clampPercent(percent: number): number {
  return Math.min(100, Math.max(0, percent));
}

/** value="" is the literal "Não alocado" option (§4.7) mapped to `null` on submit. */
export function vaultSelectOptions(
  t: TFunction,
  vaults: Vault[],
  excludeVaultId?: string,
): SelectOption[] {
  return [
    { value: "", label: t("vaults.unallocatedOption") },
    ...activeVaults(vaults)
      .filter((vault) => vault.id !== excludeVaultId)
      .map((vault) => ({ value: vault.id, label: vault.name })),
  ];
}

/** select value → payload: the empty string is the unallocated slice, i.e. `null`. */
export function selectValueToVaultId(value: string): string | null {
  return value === "" ? null : value;
}

export function goalStatusBadgeClass(status: VaultGoalStatus): string {
  switch (status) {
    case VaultGoalStatus.REACHED:
      return "bg-emerald-100 text-emerald-900 dark:bg-emerald-900/40 dark:text-emerald-100";
    case VaultGoalStatus.OVERDUE:
      return "bg-destructive/15 text-destructive";
    case VaultGoalStatus.IN_PROGRESS:
    default:
      return "bg-blue-100 text-blue-900 dark:bg-blue-900/40 dark:text-blue-100";
  }
}

/** Green for money that arrived in the vault, red for money that left (signed amount, §4.1). */
export function entryAmountClass(amountCents: number): string {
  return amountCents < 0 ? "text-destructive" : "text-emerald-700 dark:text-emerald-400";
}

/**
 * The goal bar renders only what §4.6 sends: REACHED and "no deadline" are
 * their own copy, and monthlyNeededCents is never null just because the
 * deadline passed (CT-2 decision 4) — OVERDUE gets its own phrasing instead
 * of a special-cased null.
 */
export function goalMonthlyLabel(
  t: TFunction,
  goal: Pick<VaultGoal, "status" | "monthlyNeededCents">,
): string {
  if (goal.status === VaultGoalStatus.REACHED) return t("vaults.goal.reached");
  if (goal.monthlyNeededCents == null) return t("vaults.goal.noDeadline");
  if (goal.status === VaultGoalStatus.OVERDUE) {
    return t("vaults.goal.monthlyNeededOverdue", { amount: formatCents(goal.monthlyNeededCents) });
  }
  return t("vaults.goal.monthlyNeeded", { amount: formatCents(goal.monthlyNeededCents) });
}
