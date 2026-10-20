import { useTranslation } from "react-i18next";

import { Badge } from "@/components/ui/badge";
import { ListRow } from "@/components/ui/list-row";
import { useFormatCents } from "@/hooks/useFormatCents";
import { useIsMobile } from "@/hooks/useMediaQuery";
import { AccountType, type DashboardSummary } from "@/lib/api/generated/mKFinanceAPI.schemas";
import {
  effectiveBalanceCents,
  projectedBalanceCents,
} from "@/features/accounts/accountOptions";

interface BalanceSectionProps {
  summary: DashboardSummary;
}

/** First two letters of an account name, for the avatar chip (mock's "NU"/"IT" style). */
function initials(name: string): string {
  return name.trim().slice(0, 2).toUpperCase();
}

/**
 * Mobile-only dark hero panel (design_handoff_mk_finance/mobile screen 01):
 * replaces the desktop's separate balance/income-expense cards with a single
 * stacked summary — saldo em contas, then entradas/saídas/projetado. Gated
 * by useIsMobile() (not CSS) so the desktop cards' matching totals never
 * co-exist in the DOM — table/list-style structural swaps in this reskin use
 * the hook rather than max-sm:/sm: so tests that assert a total appears once
 * keep working without change.
 */
function BalanceHeroMobile({ summary }: BalanceSectionProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  // Same population as totalBalanceCents (excludes INVESTMENT, excludes archived).
  const projectedTotal = summary.accounts
    .filter((account) => !account.archived && account.type !== AccountType.INVESTMENT)
    .reduce((sum, account) => sum + projectedBalanceCents(account), 0);

  return (
    <div className="flex flex-col gap-3.5 rounded-card bg-hero p-[18px] text-hero-foreground">
      <div className="flex flex-col gap-0.5">
        <span className="text-[11px] text-[#8D9298]">{t("dashboard.hero.balanceLabel")}</span>
        <span className="text-[30px] font-extrabold tracking-[-0.035em] tabular-nums text-hero-foreground">
          {formatCents(summary.totalBalanceCents)}
        </span>
      </div>
      <div className="h-px bg-white/10" />
      <div className="flex gap-2.5">
        <div className="flex flex-1 flex-col gap-0.5">
          <span className="text-[10px] text-[#8D9298]">{t("dashboard.hero.income")}</span>
          <span className="text-[15px] font-extrabold tabular-nums text-hero-foreground">
            {formatCents(summary.incomeCents)}
          </span>
        </div>
        <div className="flex flex-1 flex-col gap-0.5">
          <span className="text-[10px] text-[#8D9298]">{t("dashboard.hero.expense")}</span>
          <span className="text-[15px] font-extrabold tabular-nums text-hero-foreground">
            {formatCents(summary.expenseCents)}
          </span>
        </div>
        <div className="flex flex-1 flex-col gap-0.5">
          <span className="text-[10px] text-[#8D9298]">{t("dashboard.hero.projected")}</span>
          <span className="text-[15px] font-extrabold tabular-nums text-mint">
            {formatCents(projectedTotal)}
          </span>
        </div>
      </div>
    </div>
  );
}

export function BalanceSection({ summary }: BalanceSectionProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const isMobile = useIsMobile();
  const accounts = summary.accounts.filter((account) => !account.archived);

  if (isMobile) {
    return <BalanceHeroMobile summary={summary} />;
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="rounded-card bg-card p-5">
        <span className="text-xs font-semibold text-ink-tertiary">
          {t("dashboard.balance.total")}
        </span>
        <div className="mt-2 text-[26px] font-extrabold tracking-[-0.03em] tabular-nums text-ink-primary sm:text-[30px]">
          {formatCents(summary.totalBalanceCents)}
        </div>
        <div className="mt-1 text-xs text-ink-tertiary">
          {t("dashboard.balance.totalWithInvestments")}{" "}
          <span className="tabular-nums">
            {formatCents(summary.totalBalanceWithInvestmentsCents)}
          </span>
        </div>
      </div>

      <div className="flex flex-1 flex-col gap-3 rounded-card bg-card p-5">
        <h3 className="text-sm font-bold text-ink-primary">{t("dashboard.balance.title")}</h3>

        {accounts.length === 0 ? (
          <p className="text-sm text-ink-tertiary">{t("dashboard.balance.empty")}</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {accounts.map((account) => (
              <li key={account.id} className="rounded-list-item bg-field px-3.5">
                <ListRow
                  leading={
                    <span className="flex size-8 items-center justify-center rounded-[10px] bg-neutral text-[11px] font-extrabold text-ink-secondary">
                      {initials(account.name)}
                    </span>
                  }
                  title={account.name}
                  subtitle={t(`accountType.${account.type}`)}
                  value={
                    <span className="flex flex-col items-end gap-1">
                      <span className="text-[13px] font-bold tabular-nums text-ink-primary">
                        {formatCents(effectiveBalanceCents(account))}
                      </span>
                      {account.type === AccountType.INVESTMENT && (
                        <Badge variant="success">{t("dashboard.balance.investmentBadge")}</Badge>
                      )}
                    </span>
                  }
                />
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
