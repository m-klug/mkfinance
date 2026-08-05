import { http, HttpResponse } from "msw";

import {
  ContributionPillar,
  TransactionStatus,
  VaultEntrySource,
  VaultGoalStatus,
  type Vault,
  type VaultComposition,
  type VaultEntry,
  type VaultReallocation,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import { investmentAccount } from "@/test/financeFixtures";

// Deterministic vaults fixtures (armadilha 9 — the orval MSW mocks are random
// faker data with a 1s delay and cannot back assertions). Mirrors the E8
// scenario from DESENVOLVIMENTO-CAIXINHAS.md §8: "Reserva" (no goal, pillar
// FINANCIAL_FREEDOM) and "Viagem 2027" (a goal, pillar GOALS).

export const reserveVault: Vault = {
  id: "vault-reserve",
  accountId: investmentAccount.id,
  name: "Reserva",
  contributionPillar: ContributionPillar.FINANCIAL_FREEDOM,
  color: "#2563eb",
  archived: false,
  balanceCents: 600000,
  projectedBalanceCents: 600000,
};

export const tripVault: Vault = {
  id: "vault-trip",
  accountId: investmentAccount.id,
  name: "Viagem 2027",
  contributionPillar: ContributionPillar.GOALS,
  color: "#f97316",
  targetAmountCents: 1200000,
  targetDate: "2027-07-01",
  archived: false,
  balanceCents: 450000,
  projectedBalanceCents: 450000,
  goal: {
    targetAmountCents: 1200000,
    targetDate: "2027-07-01",
    remainingCents: 750000,
    progressPercent: 37.5,
    monthsRemaining: 12,
    monthlyNeededCents: 62500,
    status: VaultGoalStatus.IN_PROGRESS,
  },
};

export const archivedVault: Vault = {
  id: "vault-old",
  accountId: investmentAccount.id,
  name: "Caixinha Antiga",
  contributionPillar: ContributionPillar.GOALS,
  archived: true,
  balanceCents: 0,
  projectedBalanceCents: 0,
};

export const defaultVaults: Vault[] = [reserveVault, tripVault];

/** R$ 10.500,00 in the account: 6.000 in Reserva, 4.500 in Viagem, 500 unallocated. */
export function vaultComposition(overrides: Partial<VaultComposition> = {}): VaultComposition {
  return {
    accountId: investmentAccount.id,
    balanceCents: 1050000,
    projectedBalanceCents: 1050000,
    allocatedCents: 1050000 - 50000,
    unallocatedCents: 50000,
    projectedUnallocatedCents: 50000,
    vaults: defaultVaults,
    ...overrides,
  };
}

export const reserveContributionEntry: VaultEntry = {
  id: "ventry-reserve-contribution",
  vaultId: reserveVault.id,
  date: "2026-07-10",
  description: "Aporte mensal",
  amountCents: 500000,
  source: VaultEntrySource.CONTRIBUTION,
  status: TransactionStatus.SETTLED,
  transactionId: "tx-vault-contribution",
};

export const reserveReallocationEntry: VaultEntry = {
  id: "ventry-reserve-reallocation",
  vaultId: reserveVault.id,
  date: "2026-07-01",
  description: "Saldo pré-existente",
  amountCents: 100000,
  source: VaultEntrySource.REALLOCATION,
  status: TransactionStatus.SETTLED,
  reallocationId: "realloc-1",
};

export const defaultVaultEntries: VaultEntry[] = [
  reserveContributionEntry,
  reserveReallocationEntry,
];

export const tripReallocation: VaultReallocation = {
  id: "realloc-2",
  accountId: investmentAccount.id,
  fromVaultId: null,
  toVaultId: tripVault.id,
  amountCents: 400000,
  date: "2026-07-01",
  description: "Saldo pré-existente",
};

interface VaultHandlerOptions {
  composition?: VaultComposition;
  entries?: VaultEntry[];
}

/**
 * Read handlers for the vaults screen. `composition` backs
 * GET .../accounts/:accountId/vaults (honouring includeArchived), `entries`
 * backs GET .../vaults/:vaultId/entries (filtered by vaultId, most recent
 * first — matches the contract's ordering).
 */
export function vaultReadHandlers({
  composition = vaultComposition(),
  entries = defaultVaultEntries,
}: VaultHandlerOptions = {}) {
  return [
    http.get("*/workspaces/:workspaceId/accounts/:accountId/vaults", ({ request }) => {
      const includeArchived = new URL(request.url).searchParams.get("includeArchived") === "true";
      if (includeArchived) return HttpResponse.json(composition);
      return HttpResponse.json({
        ...composition,
        vaults: composition.vaults.filter((vault) => !vault.archived),
      });
    }),
    http.get("*/workspaces/:workspaceId/vaults/:vaultId/entries", ({ params }) => {
      const matching = entries
        .filter((entry) => entry.vaultId === params.vaultId)
        .sort((a, b) => (a.date < b.date ? 1 : -1));
      return HttpResponse.json(matching);
    }),
  ];
}
