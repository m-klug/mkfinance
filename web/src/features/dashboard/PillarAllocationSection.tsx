import { useTranslation } from "react-i18next";
import { AlertTriangle } from "lucide-react";

import { cn } from "@/lib/utils";
import { useFormatCents } from "@/hooks/useFormatCents";
import { Badge } from "@/components/ui/badge";
import { TargetBar } from "@/components/ui/target-bar";
import type { PillarAllocationReport } from "@/lib/api/generated/mKFinanceAPI.schemas";
import {
  hasAnyOverrun,
  isPillarOverTarget,
  sortPillars,
} from "@/features/dashboard/dashboardData";
import { formatPercent, PILLAR_COLORS } from "@/features/dashboard/dashboardFormat";

interface PillarAllocationSectionProps {
  report: PillarAllocationReport;
}

/**
 * The app's signature panel (design_handoff_mk_finance/README.md §Screens 01/04):
 * dark hero card with a real-vs-target TargetBar per pillar. Kept always-dark
 * (bg-hero) regardless of the app theme, matching the handoff's normative
 * light×dark reference.
 */
export function PillarAllocationSection({ report }: PillarAllocationSectionProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const pillars = sortPillars(report.pillars);
  const overrun = hasAnyOverrun(pillars);
  const overrunCount = pillars.filter(isPillarOverTarget).length;

  const totalSpentCents = pillars.reduce((sum, pillar) => sum + pillar.spentCents, 0);
  const totalActualPercent = report.noIncome
    ? null
    : pillars.reduce((sum, pillar) => sum + (pillar.actualPercent ?? 0), 0);
  const unallocatedCents = report.noIncome ? null : report.incomeCents - totalSpentCents;

  return (
    <div className="flex flex-col gap-5 rounded-card bg-hero p-6 text-hero-foreground">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 className="text-[17px] font-bold tracking-[-0.015em] sm:text-[19px]">
            {t("dashboard.allocation.title")}
          </h2>
          <p className="text-xs text-[#8D9298]">{t("dashboard.allocation.subtitle")}</p>
        </div>
      </div>

      {report.noIncome && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-list-item bg-[#22252A] p-3 text-sm text-hero-foreground"
        >
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
          {t("dashboard.allocation.noIncomeWarning")}
        </p>
      )}
      {!report.noIncome && overrun && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-list-item bg-[#3A201A] p-3 text-sm text-[#FFB4A2]"
        >
          <AlertTriangle className="mt-0.5 size-4 shrink-0" aria-hidden />
          {t("dashboard.allocation.overrunWarning", { count: overrunCount })}
        </p>
      )}

      <ul className="flex flex-col">
        {pillars.map((pillar) => {
          const over = isPillarOverTarget(pillar);
          const color = PILLAR_COLORS[pillar.pillar];
          const actual = pillar.actualPercent;
          return (
            <li
              key={pillar.pillar}
              className={cn(
                "grid grid-cols-[minmax(0,1fr)_92px] items-center gap-3 border-t border-white/10 py-3 first:border-t-0 sm:grid-cols-[140px_minmax(0,1fr)_92px_100px] sm:gap-4",
              )}
            >
              <div className="col-span-2 flex min-w-0 items-center gap-2 sm:col-span-1">
                <span
                  aria-hidden
                  className="h-[20px] w-[6px] shrink-0 rounded-pill"
                  style={{ backgroundColor: color }}
                />
                <span className="truncate text-[13px] font-semibold text-[#E7EAE8]">
                  {t(`pillar.${pillar.pillar}`)}
                </span>
              </div>

              {!report.noIncome && (
                <TargetBar
                  actualPercent={actual ?? 0}
                  targetPercent={pillar.targetPercent}
                  color={color}
                  className="order-3 col-span-2 h-[9px] bg-[#22252A] sm:order-none sm:col-span-1"
                />
              )}

              <div
                className={cn(
                  "text-right tabular-nums",
                  report.noIncome && "col-span-2 sm:col-span-3",
                )}
              >
                {report.noIncome ? (
                  <div className="text-[15px] font-extrabold text-hero-foreground">
                    {formatCents(pillar.spentCents)}
                  </div>
                ) : (
                  <>
                    <div className="text-[15px] font-extrabold text-hero-foreground sm:text-[18px]">
                      {formatPercent(actual ?? 0)}
                    </div>
                    <div className="text-[10px] text-[#8D9298]">
                      {t("dashboard.allocation.targetTitle", { target: pillar.targetPercent })}
                    </div>
                  </>
                )}
              </div>

              {!report.noIncome && (
                <div className="hidden text-right tabular-nums sm:block">
                  <div className="text-[13px] font-semibold text-[#C9CDD1]">
                    {formatCents(pillar.spentCents)}
                  </div>
                  {over && (
                    <Badge variant="alert" className="mt-1 bg-[#3A201A] text-[#FFB4A2]">
                      {t("dashboard.allocation.overrunBadge")}
                    </Badge>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ul>

      {!report.noIncome && (
        <div className="flex flex-wrap items-center gap-6 border-t border-white/10 pt-4">
          <div className="flex flex-col gap-0.5">
            <span className="text-[11px] text-[#8D9298]">
              {t("dashboard.allocation.allocatedLabel")}
            </span>
            <span className="text-[15px] font-bold tabular-nums text-hero-foreground">
              {formatPercent(totalActualPercent ?? 0)} · {formatCents(totalSpentCents)}
            </span>
          </div>
          <div className="flex flex-col gap-0.5">
            <span className="text-[11px] text-[#8D9298]">
              {t("dashboard.allocation.unallocatedLabel")}
            </span>
            <span className="text-[15px] font-bold tabular-nums text-mint">
              {formatCents(unallocatedCents ?? 0)}
            </span>
          </div>
          <div className="ml-auto flex items-center gap-4 text-[11px] text-[#8D9298]">
            <span className="flex items-center gap-1.5">
              <span aria-hidden className="h-2 w-4 rounded-pill bg-mint" />
              {t("dashboard.allocation.legendActual")}
            </span>
            <span className="flex items-center gap-1.5">
              <span aria-hidden className="h-3.5 w-0.5 bg-hero-foreground" />
              {t("dashboard.allocation.legendTarget")}
            </span>
          </div>
        </div>
      )}
    </div>
  );
}
