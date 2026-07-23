import { Suspense, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { formatCents } from "@/lib/format";
import {
  Pillar,
  type Category,
  type DashboardSummaryExpenseByCategoryItem,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import {
  pillarsWithSpend,
  resolveCategorySpends,
  sumCents,
} from "@/features/dashboard/dashboardData";
import { formatPercent, PILLAR_COLORS } from "@/features/dashboard/dashboardFormat";
import { useContainerWidth } from "@/features/dashboard/useContainerWidth";
import { LazyCategoryPieChart } from "@/features/dashboard/charts/lazy";

interface CategoryBreakdownSectionProps {
  items: DashboardSummaryExpenseByCategoryItem[];
  categories: Category[];
}

type PillarFilter = Pillar | "ALL";

export function CategoryBreakdownSection({ items, categories }: CategoryBreakdownSectionProps) {
  const { t } = useTranslation();
  const { ref, width } = useContainerWidth<HTMLDivElement>();
  const [filter, setFilter] = useState<PillarFilter>("ALL");

  const allSpends = useMemo(
    () => resolveCategorySpends(items, categories, t("dashboard.categories.unknown")),
    [items, categories, t],
  );
  const availablePillars = useMemo(() => pillarsWithSpend(allSpends), [allSpends]);

  // Drill-down: the whole breakdown, or one pillar's categories.
  const spends = useMemo(
    () => (filter === "ALL" ? allSpends : allSpends.filter((spend) => spend.pillar === filter)),
    [allSpends, filter],
  );
  const total = sumCents(spends);

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("dashboard.categories.title")}</CardTitle>
        <CardDescription>{t("dashboard.categories.subtitle")}</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {allSpends.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("dashboard.categories.empty")}</p>
        ) : (
          <>
            <div className="flex flex-wrap gap-2" role="group" aria-label={t("dashboard.categories.filterLabel")}>
              <button
                type="button"
                aria-pressed={filter === "ALL"}
                onClick={() => setFilter("ALL")}
                className={cn(
                  "rounded-full border px-3 py-1 text-xs",
                  filter === "ALL"
                    ? "border-primary bg-primary text-primary-foreground"
                    : "text-muted-foreground",
                )}
              >
                {t("dashboard.categories.allPillars")}
              </button>
              {availablePillars.map((pillar) => (
                <button
                  key={pillar}
                  type="button"
                  aria-pressed={filter === pillar}
                  onClick={() => setFilter(pillar)}
                  className={cn(
                    "rounded-full border px-3 py-1 text-xs",
                    filter === pillar
                      ? "border-primary bg-primary text-primary-foreground"
                      : "text-muted-foreground",
                  )}
                >
                  {t(`pillar.${pillar}`)}
                </button>
              ))}
            </div>

            <div ref={ref} className="min-h-[220px]">
              {width > 0 && (
                <Suspense fallback={null}>
                  <LazyCategoryPieChart spends={spends} width={width} />
                </Suspense>
              )}
            </div>

            <ul className="flex flex-col divide-y">
              {spends.map((spend) => (
                <li key={spend.categoryId} className="flex items-center justify-between gap-2 py-2">
                  <span className="flex items-center gap-2 text-sm">
                    <span
                      aria-hidden
                      className="size-2.5 shrink-0 rounded-full"
                      style={{ backgroundColor: PILLAR_COLORS[spend.pillar] }}
                    />
                    {spend.name}
                    <span className="text-xs text-muted-foreground">{t(`pillar.${spend.pillar}`)}</span>
                  </span>
                  <span className="flex items-center gap-2 text-sm tabular-nums">
                    {formatCents(spend.amountCents)}
                    {total > 0 && (
                      <span className="text-xs text-muted-foreground">
                        {formatPercent((spend.amountCents / total) * 100)}
                      </span>
                    )}
                  </span>
                </li>
              ))}
            </ul>
          </>
        )}
      </CardContent>
    </Card>
  );
}
