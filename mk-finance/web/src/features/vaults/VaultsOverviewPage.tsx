import { useMemo } from "react";
import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { useQueries } from "@tanstack/react-query";
import { Plus } from "lucide-react";

import { useFormatCents } from "@/hooks/useFormatCents";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import { useListAccounts } from "@/lib/api/generated/accounts/accounts";
import { getListVaultsQueryOptions } from "@/lib/api/generated/vaults/vaults";
import type { Pillar, Vault } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { PILLAR_COLORS } from "@/features/dashboard/dashboardFormat";

/**
 * Mobile-only "Caixas" tab (design_handoff_mk_finance/README.md §Mobile,
 * mobile screen 05 — Caixinhas): every vault across every account, in one
 * flat list. The API only lists vaults per account (R9 — a vault always
 * belongs to one account), so this aggregates client-side: one query per
 * account that has vaults, joined here.
 */
export default function VaultsOverviewPage() {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const workspaceId = useCurrentWorkspaceId();
  const enabled = Boolean(workspaceId);

  const { data: accountsData } = useListAccounts(workspaceId ?? "", undefined, { query: { enabled } });
  const vaultedAccounts = useMemo(
    () => (accountsData ?? []).filter((account) => account.hasVaults && !account.archived),
    [accountsData],
  );

  const compositionQueries = useQueries({
    queries: vaultedAccounts.map((account) => ({
      ...getListVaultsQueryOptions(workspaceId ?? "", account.id),
      enabled,
    })),
  });

  const vaults = useMemo(
    () =>
      compositionQueries
        .flatMap((query) => query.data?.vaults ?? [])
        .filter((vault) => !vault.archived),
    [compositionQueries],
  );

  const totals = useMemo(
    () =>
      vaults.reduce(
        (acc, vault) => ({
          savedCents: acc.savedCents + vault.balanceCents,
          targetCents: acc.targetCents + (vault.targetAmountCents ?? 0),
        }),
        { savedCents: 0, targetCents: 0 },
      ),
    [vaults],
  );

  const isLoading = compositionQueries.some((query) => query.isPending) && vaultedAccounts.length > 0;

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center gap-2.5">
        <div className="flex flex-col">
          <h1 className="text-xl font-extrabold tracking-[-0.025em] text-ink-primary">
            {t("vaults.title")}
          </h1>
          <p className="text-[11px] text-ink-tertiary">
            {t("vaults.activeGoals", { count: vaults.length })}
          </p>
        </div>
        <Link
          to="/accounts"
          aria-label={t("vaults.new")}
          className="ml-auto flex size-11 shrink-0 items-center justify-center rounded-field bg-mint text-mint-ink"
        >
          <Plus className="size-5" strokeWidth={2.5} />
        </Link>
      </div>

      <div className="flex flex-col gap-2.5 rounded-card bg-hero p-4.5 text-hero-foreground">
        <div className="flex items-end">
          <div className="flex flex-col gap-0.5">
            <span className="text-[11px] text-hero-foreground/55">{t("vaults.saved")}</span>
            <span className="tabular-nums text-[28px] font-extrabold tracking-[-0.035em]">
              {formatCents(totals.savedCents)}
            </span>
          </div>
          <span className="ml-auto text-[11px] text-hero-foreground/55">
            {t("vaults.savedOf", { amount: formatCents(totals.targetCents) })}
          </span>
        </div>
        <div className="relative h-[9px] w-full overflow-hidden rounded-pill bg-hero-foreground/10">
          <div
            className="h-full rounded-pill bg-mint"
            style={{
              width: `${totals.targetCents > 0 ? Math.min(100, (totals.savedCents / totals.targetCents) * 100) : 0}%`,
            }}
          />
        </div>
      </div>

      <div className="flex flex-col gap-2.5">
        {!isLoading && vaults.length === 0 && (
          <p className="rounded-card bg-card p-4 text-sm text-ink-tertiary">
            {t("vaults.overviewEmpty")}
          </p>
        )}
        {vaults.map((vault) => (
          <VaultOverviewCard key={vault.id} vault={vault} formatCents={formatCents} />
        ))}
      </div>
    </div>
  );
}

function VaultOverviewCard({
  vault,
  formatCents,
}: {
  vault: Vault;
  formatCents: (cents: number) => string;
}) {
  const { t } = useTranslation();
  const percent = vault.goal?.progressPercent ?? 0;
  const color = PILLAR_COLORS[vault.contributionPillar as unknown as Pillar];

  return (
    <Link
      to={`/accounts/${vault.accountId}/vaults`}
      className="flex flex-col gap-2 rounded-card bg-card p-4"
    >
      <div className="flex items-center gap-2.5">
        <span
          className="flex size-[38px] shrink-0 items-center justify-center rounded-[13px] text-[13px] font-extrabold"
          style={{ backgroundColor: `color-mix(in srgb, ${color} 22%, transparent)`, color }}
        >
          {vault.name.slice(0, 2).toUpperCase()}
        </span>
        <div className="flex min-w-0 flex-col">
          <span className="truncate text-[13px] font-extrabold text-ink-primary">{vault.name}</span>
          <span className="truncate text-[11px] text-ink-tertiary">
            {t(`pillar.${vault.contributionPillar}`)}
          </span>
        </div>
        {vault.goal && (
          <span className="ml-auto shrink-0 tabular-nums text-[13px] font-extrabold text-ink-primary">
            {Math.round(percent)}%
          </span>
        )}
      </div>
      {vault.goal && (
        <>
          <div className="relative h-[9px] w-full overflow-hidden rounded-pill bg-neutral">
            <div
              className="h-full rounded-pill bg-mint"
              style={{ width: `${Math.min(100, percent)}%` }}
            />
          </div>
          <div className="flex justify-between text-[11px] tabular-nums text-ink-tertiary">
            <span>{formatCents(vault.balanceCents)}</span>
            <span>{t("vaults.goal.target", { amount: formatCents(vault.goal.targetAmountCents) })}</span>
          </div>
        </>
      )}
    </Link>
  );
}
