import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { TriangleAlert } from "lucide-react";

import { useFormatCents } from "@/hooks/useFormatCents";
import { useGetInvestmentSummary } from "@/lib/api/generated/investments/investments";

interface InvestmentSummarySectionProps {
  workspaceId: string;
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-baseline justify-between gap-3 py-1.5">
      <span className="text-[12px] text-ink-secondary">{label}</span>
      <span className="text-[13px] font-bold tabular-nums text-ink-primary">{value}</span>
    </div>
  );
}

/**
 * Net worth as of today. The balance of an account only gains a title's yield when the title is paid,
 * so the estimated net worth adds the yield already accrued. Card bills still owed are shown beside
 * it and never subtracted. When the positions of an account claim more than the account holds, the
 * section says so and what to do about it (usually: raise the account's initial balance).
 *
 * Renders nothing while loading or if the summary cannot be read: the positions below do not depend on it.
 */
export function InvestmentSummarySection({ workspaceId }: InvestmentSummarySectionProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const { data: summary } = useGetInvestmentSummary(workspaceId);
  if (!summary) return null;

  const fixedIncome = summary.assetClasses.find((c) => c.code === "FIXED_INCOME");
  const accrued = fixedIncome?.accruedYieldCents ?? 0;
  const exceeding = summary.accounts.filter((account) => account.positionsExceedBalance);

  return (
    <section className="flex flex-col gap-3" aria-label={t("investments.summary.title")}>
      <div className="rounded-card bg-hero p-5 text-hero-foreground">
        <p className="text-xs text-[#9AA0A6]">{t("investments.summary.netWorth")}</p>
        <p className="mt-1 text-3xl font-extrabold tabular-nums" data-testid="net-worth">
          {formatCents(summary.estimatedNetWorthCents)}
        </p>
        {accrued !== 0 && (
          <p className="mt-2 text-xs text-[#9AA0A6]">
            {t("investments.summary.includesAccrued", { amount: formatCents(accrued) })}
          </p>
        )}
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="rounded-list-item bg-field p-3.5">
          <p className="text-xs font-semibold text-ink-tertiary">{t("investments.summary.accountsAndCash")}</p>
          <p className="mt-1 text-lg font-extrabold tabular-nums text-ink-primary" data-testid="accounts-and-cash">
            {formatCents(summary.accountsAndCashCents)}
          </p>
        </div>
        <div className="rounded-list-item bg-field p-3.5">
          <p className="text-xs font-semibold text-ink-tertiary">{t("investments.summary.investments")}</p>
          <p className="mt-1 text-lg font-extrabold tabular-nums text-ink-primary" data-testid="investments-total">
            {formatCents(summary.investmentsCents)}
          </p>
        </div>
      </div>

      {summary.openStatementsCents > 0 && (
        <p className="px-1 text-xs text-ink-tertiary" data-testid="open-statements">
          {t("investments.summary.openStatements", { amount: formatCents(summary.openStatementsCents) })}
        </p>
      )}

      {fixedIncome && fixedIncome.positions > 0 && (
        <div className="rounded-card bg-card px-5 py-3">
          <div className="flex items-baseline justify-between gap-3 pb-1">
            <h3 className="text-sm font-bold text-ink-primary">{t("investments.summary.fixedIncome")}</h3>
            <span className="text-[11px] text-ink-tertiary">
              {t("investments.summary.positions", { count: fixedIncome.positions })}
            </span>
          </div>
          <Row label={t("investments.summary.applied")} value={formatCents(fixedIncome.appliedCents)} />
          <div className="h-px bg-divider-row" />
          <Row label={t("investments.summary.value")} value={formatCents(fixedIncome.estimatedValueCents)} />
          <div className="h-px bg-divider-row" />
          <Row label={t("investments.summary.yield")} value={formatCents(fixedIncome.accruedYieldCents)} />
          {fixedIncome.positionsWithoutEstimate > 0 && (
            <p className="pt-1 text-[11px] text-ink-tertiary">
              {t("investments.summary.withoutEstimate", { count: fixedIncome.positionsWithoutEstimate })}
            </p>
          )}
        </div>
      )}

      {summary.accounts.length > 0 && (
        <div className="rounded-card bg-card px-5 py-3">
          <h3 className="pb-1 text-sm font-bold text-ink-primary">{t("investments.summary.accountsTitle")}</h3>
          {summary.accounts.map((account, index) => (
            <div key={account.accountId} data-testid="summary-account">
              {index > 0 && <div className="my-2 h-px bg-divider-row" />}
              <p className="text-[13px] font-extrabold text-ink-primary">{account.name}</p>
              <Row label={t("investments.summary.balance")} value={formatCents(account.balanceCents)} />
              <Row label={t("investments.summary.inPositions")} value={formatCents(account.positionsPrincipalCents)} />
              {account.unregisteredCents > 0 && (
                <Row label={t("investments.summary.unregistered")} value={formatCents(account.unregisteredCents)} />
              )}
            </div>
          ))}
        </div>
      )}

      {exceeding.length > 0 && (
        <div role="alert" className="flex gap-3 rounded-card bg-alert-bg p-4 text-alert-ink">
          <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
          <div className="flex flex-col gap-1 text-xs">
            <p className="font-extrabold">
              {t("investments.summary.exceedTitle")} ({exceeding.map((account) => account.name).join(", ")})
            </p>
            <p>{t("investments.summary.exceedHint")}</p>
            <Link to="/accounts" className="font-bold underline">
              {t("investments.summary.editAccounts")}
            </Link>
          </div>
        </div>
      )}
    </section>
  );
}
