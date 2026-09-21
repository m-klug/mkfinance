import { Suspense, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { cn } from "@/lib/utils";
import { useFormatCents } from "@/hooks/useFormatCents";
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
  const formatCents = useFormatCents();
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
    <div className="flex flex-col gap-4 rounded-card bg-card p-5">
      <div className="flex flex-col gap-0.5">
        <h3 className="text-sm font-bold text-ink-primary">{t("dashboard.categories.title")}</h3>
        <p className="text-xs text-ink-tertiary">{t("dashboard.categories.subtitle")}</p>
      </div>

      {allSpends.length === 0 ? (
        <p className="text-sm text-ink-tertiary">{t("dashboard.categories.empty")}</p>
      ) : (
        <>
          <div
            className="flex flex-wrap gap-2"
            role="group"
            aria-label={t("dashboard.categories.filterLabel")}
          >
            <button
              type="button"
              aria-pressed={filter === "ALL"}
              onClick={() => setFilter("ALL")}
              className={cn(
                "rounded-pill px-3 py-1.5 text-xs font-bold transition-colors",
                filter === "ALL"
                  ? "bg-hero text-hero-foreground"
                  : "bg-neutral text-ink-secondary hover:text-ink-primary",
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
                  "rounded-pill px-3 py-1.5 text-xs font-bold transition-colors",
                  filter === pillar
                    ? "bg-hero text-hero-foreground"
                    : "bg-neutral text-ink-secondary hover:text-ink-primary",
                )}
              >
                {t(`pillar.${pillar}`)}
              </button>
            ))}
          </div>

          <div ref={ref} className="relative min-h-[220px]">
            {width > 0 && (
              <>
                <Suspense fallback={null}>
                  <LazyCategoryPieChart spends={spends} width={width} />
                </Suspense>
                <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
                  <span className="text-[10px] text-ink-label">
                    {t("dashboard.categories.totalLabel")}
                  </span>
                  <span className="text-[15px] font-extrabold tracking-[-0.02em] tabular-nums text-ink-primary">
                    {formatCents(total)}
                  </span>
                </div>
              </>
            )}
          </div>

          <ul className="flex flex-col gap-2">
            {spends.map((spend) => (
              <li
                key={spend.categoryId}
                className="flex items-center gap-2.5 rounded-list-item bg-field px-3 py-2"
              >
                <span
                  aria-hidden
                  className="size-2 shrink-0 rounded-swatch"
                  style={{ backgroundColor: PILLAR_COLORS[spend.pillar] }}
                />
                <span className="truncate text-[13px] font-semibold text-ink-primary">
                  {spend.name}
                </span>
                <span className="hidden text-xs text-ink-tertiary sm:inline">
                  {t(`pillar.${spend.pillar}`)}
                </span>
                <span className="ml-auto flex items-center gap-2 tabular-nums">
                  <span className="text-xs text-ink-tertiary">{formatCents(spend.amountCents)}</span>
                  {total > 0 && (
                    <span className="w-11 text-right text-xs font-bold text-ink-primary">
                      {formatPercent((spend.amountCents / total) * 100)}
                    </span>
                  )}
                </span>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}
