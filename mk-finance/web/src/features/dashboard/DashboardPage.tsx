import { useState } from "react";
import { useTranslation } from "react-i18next";

import { translateApiError } from "@/lib/errors";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import {
  useGetDashboardSummary,
  useGetPillarAllocation,
} from "@/lib/api/generated/dashboard/dashboard";
import { useListCategories } from "@/lib/api/generated/categories/categories";
import { useListCards } from "@/lib/api/generated/cards/cards";
import { currentMonth } from "@/features/dashboard/dashboardData";
import { MonthNavigator } from "@/features/dashboard/MonthNavigator";
import { PillarAllocationSection } from "@/features/dashboard/PillarAllocationSection";
import { BalanceSection } from "@/features/dashboard/BalanceSection";
import { IncomeExpenseSection } from "@/features/dashboard/IncomeExpenseSection";
import { CategoryBreakdownSection } from "@/features/dashboard/CategoryBreakdownSection";
import { OpenStatementsSection } from "@/features/dashboard/OpenStatementsSection";

export default function DashboardPage() {
  const { t } = useTranslation();
  const workspaceId = useCurrentWorkspaceId();
  const [month, setMonth] = useState(() => currentMonth());
  const enabled = Boolean(workspaceId);

  const summaryQuery = useGetDashboardSummary(
    workspaceId ?? "",
    { month },
    { query: { enabled } },
  );
  const allocationQuery = useGetPillarAllocation(
    workspaceId ?? "",
    { month },
    { query: { enabled } },
  );
  const categoriesQuery = useListCategories(workspaceId ?? "", undefined, {
    query: { enabled },
  });
  const cardsQuery = useListCards(
    workspaceId ?? "",
    { includeArchived: true },
    { query: { enabled } },
  );

  const summary = summaryQuery.data;
  const allocation = allocationQuery.data;
  const isPending = summaryQuery.isPending || allocationQuery.isPending;
  const isError = summaryQuery.isError || allocationQuery.isError;

  return (
    <div className="flex flex-1 flex-col gap-6 p-4 md:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">{t("nav.dashboard")}</h1>
        <MonthNavigator month={month} onMonthChange={setMonth} />
      </div>

      {isPending && <p className="text-muted-foreground">{t("common.loading")}</p>}

      {!isPending && isError && (
        <p role="alert" className="text-sm text-destructive">
          {translateApiError(t, summaryQuery.error ?? allocationQuery.error)}
        </p>
      )}

      {!isPending && !isError && summary && allocation && (
        <div className="flex flex-col gap-6">
          <PillarAllocationSection report={allocation} />

          <div className="grid gap-6 lg:grid-cols-2">
            <BalanceSection summary={summary} />
            <IncomeExpenseSection summary={summary} />
          </div>

          <CategoryBreakdownSection
            items={summary.expenseByCategory}
            categories={categoriesQuery.data ?? []}
          />

          <OpenStatementsSection
            statements={summary.openStatements}
            cards={cardsQuery.data ?? []}
          />
        </div>
      )}
    </div>
  );
}
