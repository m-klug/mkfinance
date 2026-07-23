import { useTranslation } from "react-i18next";
import { AlertTriangle } from "lucide-react";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { formatCents } from "@/lib/format";
import type { PillarAllocationReport } from "@/lib/api/generated/mKFinanceAPI.schemas";
import {
  hasAnyOverrun,
  isPillarOverTarget,
  sortPillars,
} from "@/features/dashboard/dashboardData";
import { formatPercent } from "@/features/dashboard/dashboardFormat";

interface PillarAllocationSectionProps {
  report: PillarAllocationReport;
}

export function PillarAllocationSection({ report }: PillarAllocationSectionProps) {
  const { t } = useTranslation();
  const pillars = sortPillars(report.pillars);
  const overrun = hasAnyOverrun(pillars);
  const overrunCount = pillars.filter(isPillarOverTarget).length;

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("dashboard.allocation.title")}</CardTitle>
        <CardDescription>{t("dashboard.allocation.subtitle")}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {report.noIncome && (
          <p
            role="status"
            className="flex items-start gap-2 rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-700 dark:text-amber-400"
          >
            <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
            {t("dashboard.allocation.noIncomeWarning")}
          </p>
        )}
        {!report.noIncome && overrun && (
          <p
            role="status"
            className="flex items-start gap-2 rounded-md border border-destructive/40 bg-destructive/10 p-3 text-sm text-destructive"
          >
            <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
            {t("dashboard.allocation.overrunWarning", { count: overrunCount })}
          </p>
        )}

        <ul className="flex flex-col gap-4">
          {pillars.map((pillar) => {
            const over = isPillarOverTarget(pillar);
            const actual = pillar.actualPercent;
            const fill = actual == null ? 0 : Math.min(actual, 100);
            const targetMarker = Math.min(pillar.targetPercent, 100);
            return (
              <li
                key={pillar.pillar}
                className={cn(
                  "rounded-md p-2",
                  over && "bg-destructive/5 ring-1 ring-destructive/30",
                )}
              >
                <div className="mb-1 flex items-baseline justify-between gap-2">
                  <span className="text-sm font-medium">{t(`pillar.${pillar.pillar}`)}</span>
                  <span className="text-sm tabular-nums text-muted-foreground">
                    {report.noIncome
                      ? formatCents(pillar.spentCents)
                      : t("dashboard.allocation.actualVsTarget", {
                          actual: formatPercent(actual ?? 0),
                          target: pillar.targetPercent,
                        })}
                  </span>
                </div>

                {!report.noIncome && (
                  <div
                    className="relative h-3 w-full overflow-hidden rounded-full bg-muted"
                    role="meter"
                    aria-label={t(`pillar.${pillar.pillar}`)}
                    aria-valuenow={Math.round(actual ?? 0)}
                    aria-valuemin={0}
                    aria-valuemax={100}
                  >
                    <div
                      className={cn(
                        "absolute inset-y-0 left-0 rounded-full transition-[width]",
                        over ? "bg-destructive" : "bg-primary",
                      )}
                      style={{ width: `${fill}%` }}
                    />
                    <div
                      className="absolute inset-y-0 w-0.5 bg-foreground/70"
                      style={{ left: `${targetMarker}%` }}
                      aria-hidden
                      title={t("dashboard.allocation.targetTitle", {
                        target: pillar.targetPercent,
                      })}
                    />
                  </div>
                )}

                <div className="mt-1 flex items-center justify-between text-xs text-muted-foreground">
                  <span className="tabular-nums">{formatCents(pillar.spentCents)}</span>
                  {over && (
                    <span className="rounded bg-destructive/15 px-2 py-0.5 font-medium text-destructive">
                      {t("dashboard.allocation.overrunBadge")}
                    </span>
                  )}
                </div>
              </li>
            );
          })}
        </ul>
      </CardContent>
    </Card>
  );
}
