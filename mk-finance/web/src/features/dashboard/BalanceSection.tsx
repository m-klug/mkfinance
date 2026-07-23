import { useTranslation } from "react-i18next";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { formatCents } from "@/lib/format";
import { AccountType, type DashboardSummary } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { effectiveBalanceCents } from "@/features/accounts/accountOptions";

interface BalanceSectionProps {
  summary: DashboardSummary;
}

export function BalanceSection({ summary }: BalanceSectionProps) {
  const { t } = useTranslation();
  const accounts = summary.accounts.filter((account) => !account.archived);

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("dashboard.balance.title")}</CardTitle>
        <CardDescription>{t("dashboard.balance.subtitle")}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="rounded-md border p-3">
            <p className="text-sm text-muted-foreground">{t("dashboard.balance.total")}</p>
            <p className="text-2xl font-semibold tabular-nums">
              {formatCents(summary.totalBalanceCents)}
            </p>
          </div>
          <div className="rounded-md border p-3">
            <p className="text-sm text-muted-foreground">
              {t("dashboard.balance.totalWithInvestments")}
            </p>
            <p className="text-2xl font-semibold tabular-nums">
              {formatCents(summary.totalBalanceWithInvestmentsCents)}
            </p>
          </div>
        </div>

        {accounts.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("dashboard.balance.empty")}</p>
        ) : (
          <ul className="flex flex-col divide-y">
            {accounts.map((account) => (
              <li key={account.id} className="flex items-center justify-between gap-2 py-2">
                <span className="flex items-center gap-2 text-sm">
                  {account.name}
                  {account.type === AccountType.INVESTMENT && (
                    <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                      {t("dashboard.balance.investmentBadge")}
                    </span>
                  )}
                </span>
                <span className="text-sm tabular-nums">
                  {formatCents(effectiveBalanceCents(account))}
                </span>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
