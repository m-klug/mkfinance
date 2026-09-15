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
    <div className="flex flex-1 flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-[26px] font-extrabold tracking-[-0.025em] text-ink-primary">
          {t("nav.dashboard")}
        </h1>
        <MonthNavigator month={month} onMonthChange={setMonth} />
      </div>

      {isPending && <p className="text-sm text-ink-tertiary">{t("common.loading")}</p>}

      {!isPending && isError && (
        <p role="alert" className="rounded-list-item bg-alert-bg p-3 text-sm text-alert-ink">
          {translateApiError(t, summaryQuery.error ?? allocationQuery.error)}
        </p>
      )}

      {!isPending && !isError && summary && allocation && (
        <div className="flex flex-col gap-5">
          <PillarAllocationSection report={allocation} />

          <div className="grid gap-5 lg:grid-cols-2">
            <BalanceSection summary={summary} />
            <IncomeExpenseSection summary={summary} />
          </div>

          <div className="grid gap-5 lg:grid-cols-2">
            <CategoryBreakdownSection
              items={summary.expenseByCategory}
              categories={categoriesQuery.data ?? []}
            />

            <OpenStatementsSection
              statements={summary.openStatements}
              cards={cardsQuery.data ?? []}
            />
          </div>
        </div>
      )}
    </div>
  );
}
