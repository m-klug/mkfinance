import { useTranslation } from "react-i18next";

import { Badge } from "@/components/ui/badge";
import { useFormatCents } from "@/hooks/useFormatCents";
import { AccountType, type DashboardSummary } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { effectiveBalanceCents } from "@/features/accounts/accountOptions";

interface BalanceSectionProps {
  summary: DashboardSummary;
}

/** First two letters of an account name, for the avatar chip (mock's "NU"/"IT" style). */
function initials(name: string): string {
  return name.trim().slice(0, 2).toUpperCase();
}

export function BalanceSection({ summary }: BalanceSectionProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const accounts = summary.accounts.filter((account) => !account.archived);

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
              <li
                key={account.id}
                className="flex items-center gap-3 rounded-list-item bg-field px-3.5 py-2.5"
              >
                <span className="flex size-8 shrink-0 items-center justify-center rounded-[10px] bg-neutral text-[11px] font-extrabold text-ink-secondary">
                  {initials(account.name)}
                </span>
                <span className="flex min-w-0 flex-col">
                  <span className="truncate text-[13px] font-bold text-ink-primary">
                    {account.name}
                  </span>
                  <span className="text-[10px] text-ink-label">
                    {t(`accountType.${account.type}`)}
                  </span>
                </span>
                <span className="ml-auto flex flex-col items-end gap-1">
                  <span className="text-[13px] font-bold tabular-nums text-ink-primary">
                    {formatCents(effectiveBalanceCents(account))}
                  </span>
                  {account.type === AccountType.INVESTMENT && (
                    <Badge variant="success">{t("dashboard.balance.investmentBadge")}</Badge>
                  )}
                </span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
