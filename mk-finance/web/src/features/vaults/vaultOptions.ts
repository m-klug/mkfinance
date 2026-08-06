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

export interface VaultYieldPreviewShare {
  vaultId: string;
  name: string;
  cents: number;
}

/**
 * Client-side mirror of `YieldDistributor` (BE-11, §4.5), for VaultYieldDialog's preview only — the
 * response's `VaultYield.shares` is what the server actually applies, and the dialog says so. Same
 * pro-rata + largest-remainder (Hamilton) rule: floor each active vault's proportional share of
 * `amountCents` from its settled balance, then hand the leftover cents one at a time to the largest
 * fractional remainder — ties broken by balance desc, then name (case-insensitive), then id, the
 * same order YieldDistributor's javadoc documents. A vault with a zero or negative balance never
 * takes part (§4.5), and an empty base (nothing to divide by) returns no shares at all rather than
 * inventing an allocation — the whole amount would land in the unallocated slice, same as the server.
 */
export function yieldPreviewShares(vaults: Vault[], amountCents: number): VaultYieldPreviewShare[] {
  const bases = activeVaults(vaults).filter((vault) => vault.balanceCents > 0);
  const totalBase = bases.reduce((sum, vault) => sum + vault.balanceCents, 0);
  if (amountCents <= 0 || totalBase <= 0) return [];

  const entries = bases.map((vault) => {
    const exact = (amountCents * vault.balanceCents) / totalBase;
    const cents = Math.floor(exact);
    return { vault, cents, remainder: exact - cents };
  });

  const distributed = entries.reduce((sum, entry) => sum + entry.cents, 0);
  const leftover = amountCents - distributed;

  const byRemainder = [...entries].sort((a, b) => {
    if (b.remainder !== a.remainder) return b.remainder - a.remainder;
    if (b.vault.balanceCents !== a.vault.balanceCents) return b.vault.balanceCents - a.vault.balanceCents;
    const nameCompare = a.vault.name.localeCompare(b.vault.name, "pt-BR", { sensitivity: "base" });
    return nameCompare !== 0 ? nameCompare : a.vault.id.localeCompare(b.vault.id);
  });
  for (let i = 0; i < leftover; i += 1) {
    byRemainder[i].cents += 1;
  }

  return entries.map((entry) => ({
    vaultId: entry.vault.id,
    name: entry.vault.name,
    cents: entry.cents,
  }));
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

/**
 * Matches every mounted vault-composition query of a workspace, whatever account it is for.
 *
 * <p>The generated key is a single URL string ({@code /workspaces/{ws}/accounts/{id}/vaults}), so
 * TanStack's usual "shorter key is a prefix" invalidation cannot reach it by workspace alone — hence
 * the predicate. Every write that moves money has to use this: a contribution booked from the
 * header's quick entry changes a vault's balance just as much as one booked from the vaults screen,
 * and without it an open composition keeps showing pre-write numbers until the 30s staleTime lapses.
 */
export function vaultCompositionQueryFilter(workspaceId: string) {
  const prefix = `/workspaces/${workspaceId}/accounts/`;
  return {
    predicate: (query: { queryKey: readonly unknown[] }) => {
      const [key] = query.queryKey;
      return typeof key === "string" && key.startsWith(prefix) && key.endsWith("/vaults");
    },
  };
}
