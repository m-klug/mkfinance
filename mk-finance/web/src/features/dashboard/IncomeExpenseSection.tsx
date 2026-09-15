import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { ArrowDownRight, ArrowUpRight, Minus } from "lucide-react";

import { cn } from "@/lib/utils";
import { formatCents } from "@/lib/format";
import type { DashboardSummary } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { computeDelta, type Delta } from "@/features/dashboard/dashboardData";
import { formatSignedPercent } from "@/features/dashboard/dashboardFormat";
import { useContainerWidth } from "@/features/dashboard/useContainerWidth";
import { LazyIncomeExpenseChart } from "@/features/dashboard/charts/lazy";
import type { IncomeExpenseDatum } from "@/features/dashboard/charts/IncomeExpenseChart";

interface IncomeExpenseSectionProps {
  summary: DashboardSummary;
}

/** A month-over-month delta pill; "higherIsWorse" flips the color meaning. */
function DeltaLine({ delta, higherIsWorse }: { delta: Delta; higherIsWorse: boolean }) {
  const { t } = useTranslation();
  if (delta.percent == null) {
    return (
      <span className="mt-2 inline-block text-[11px] text-ink-tertiary">
        {t("dashboard.incomeExpense.noComparison")}
      </span>
    );
  }
  const rising = delta.cents > 0;
  const flat = delta.cents === 0;
  const bad = higherIsWorse ? rising : !rising && !flat;
  const Icon = flat ? Minus : rising ? ArrowUpRight : ArrowDownRight;
  return (
    <span
      className={cn(
        "mt-2 inline-flex items-center gap-1 rounded-pill px-2 py-0.5 text-[10px] font-bold",
        flat ? "bg-neutral text-ink-secondary" : bad ? "bg-alert-bg text-alert-ink" : "bg-success-bg text-success-ink",
      )}
    >
      <Icon className="size-3" aria-hidden />
      {t("dashboard.incomeExpense.vsPrevious", { value: formatSignedPercent(delta.percent) })}
    </span>
  );
}

export function IncomeExpenseSection({ summary }: IncomeExpenseSectionProps) {
  const { t } = useTranslation();
  const { ref, width } = useContainerWidth<HTMLDivElement>();
  const net = summary.incomeCents - summary.expenseCents;

  const incomeDelta = computeDelta(summary.incomeCents, summary.previousMonthIncomeCents);
  const expenseDelta = computeDelta(summary.expenseCents, summary.previousMonthExpenseCents);

  const chartData: IncomeExpenseDatum[] = [
    { label: t("dashboard.incomeExpense.income"), amountCents: summary.incomeCents, kind: "income" },
    { label: t("dashboard.incomeExpense.expense"), amountCents: summary.expenseCents, kind: "expense" },
  ];

  return (
    <div className="flex flex-col gap-4 rounded-card bg-card p-5">
      <div className="flex flex-col gap-0.5">
        <h3 className="text-sm font-bold text-ink-primary">{t("dashboard.incomeExpense.title")}</h3>
        <p className="text-xs text-ink-tertiary">{t("dashboard.incomeExpense.subtitle")}</p>
      </div>

      <div className="grid gap-3 sm:grid-cols-3">
        <div className="rounded-list-item bg-field p-3.5">
          <p className="text-xs font-semibold text-ink-tertiary">{t("dashboard.incomeExpense.income")}</p>
          <p className="mt-1 text-xl font-extrabold tabular-nums text-ink-primary">
            {formatCents(summary.incomeCents)}
          </p>
          <DeltaLine delta={incomeDelta} higherIsWorse={false} />
        </div>
        <div className="rounded-list-item bg-field p-3.5">
          <p className="text-xs font-semibold text-ink-tertiary">{t("dashboard.incomeExpense.expense")}</p>
          <p className="mt-1 text-xl font-extrabold tabular-nums text-ink-primary">
            {formatCents(summary.expenseCents)}
          </p>
          <DeltaLine delta={expenseDelta} higherIsWorse />
        </div>
        <div className="rounded-list-item bg-hero p-3.5 text-hero-foreground">
          <p className="text-xs text-[#9AA0A6]">{t("dashboard.incomeExpense.net")}</p>
          <p
            className={cn(
              "mt-1 text-xl font-extrabold tabular-nums",
              net < 0 ? "text-[#FFB4A2]" : "text-hero-foreground",
            )}
          >
            {formatCents(net)}
          </p>
        </div>
      </div>

      <div ref={ref} className="min-h-[220px]">
        {width > 0 && (
          <Suspense fallback={null}>
            <LazyIncomeExpenseChart data={chartData} width={width} />
          </Suspense>
        )}
      </div>
    </div>
  );
}
