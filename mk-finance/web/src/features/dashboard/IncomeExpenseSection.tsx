import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import { ArrowDownRight, ArrowUpRight, Minus } from "lucide-react";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
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

/** A month-over-month delta line; "higherIsWorse" flips the color meaning. */
function DeltaLine({ delta, higherIsWorse }: { delta: Delta; higherIsWorse: boolean }) {
  const { t } = useTranslation();
  if (delta.percent == null) {
    return <span className="text-xs text-muted-foreground">{t("dashboard.incomeExpense.noComparison")}</span>;
  }
  const rising = delta.cents > 0;
  const flat = delta.cents === 0;
  const bad = higherIsWorse ? rising : !rising && !flat;
  const Icon = flat ? Minus : rising ? ArrowUpRight : ArrowDownRight;
  return (
    <span
      className={cn(
        "flex items-center gap-1 text-xs",
        flat ? "text-muted-foreground" : bad ? "text-destructive" : "text-emerald-600 dark:text-emerald-400",
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
    <Card>
      <CardHeader>
        <CardTitle>{t("dashboard.incomeExpense.title")}</CardTitle>
        <CardDescription>{t("dashboard.incomeExpense.subtitle")}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="rounded-md border p-3">
            <p className="text-sm text-muted-foreground">{t("dashboard.incomeExpense.income")}</p>
            <p className="text-xl font-semibold tabular-nums text-emerald-600 dark:text-emerald-400">
              {formatCents(summary.incomeCents)}
            </p>
            <DeltaLine delta={incomeDelta} higherIsWorse={false} />
          </div>
          <div className="rounded-md border p-3">
            <p className="text-sm text-muted-foreground">{t("dashboard.incomeExpense.expense")}</p>
            <p className="text-xl font-semibold tabular-nums text-destructive">
              {formatCents(summary.expenseCents)}
            </p>
            <DeltaLine delta={expenseDelta} higherIsWorse />
          </div>
          <div className="rounded-md border p-3">
            <p className="text-sm text-muted-foreground">{t("dashboard.incomeExpense.net")}</p>
            <p
              className={cn(
                "text-xl font-semibold tabular-nums",
                net < 0 && "text-destructive",
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
      </CardContent>
    </Card>
  );
}
