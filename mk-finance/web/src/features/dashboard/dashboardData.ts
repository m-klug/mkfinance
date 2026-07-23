import {
  Pillar,
  type Category,
  type DashboardSummaryExpenseByCategoryItem,
  type PillarAllocationReportPillarsItem,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

// Canonical pillar order (PLANEJAMENTO §6.6 / the six-pillar method). The API
// may return pillars in any order; the dashboard always renders them like this.
export const PILLAR_ORDER: Pillar[] = [
  Pillar.FIXED_COSTS,
  Pillar.COMFORT,
  Pillar.GOALS,
  Pillar.PLEASURES,
  Pillar.FINANCIAL_FREEDOM,
  Pillar.KNOWLEDGE,
];

/** Stable sort of an allocation report by the canonical pillar order. */
export function sortPillars(
  pillars: PillarAllocationReportPillarsItem[],
): PillarAllocationReportPillarsItem[] {
  return [...pillars].sort(
    (a, b) => PILLAR_ORDER.indexOf(a.pillar) - PILLAR_ORDER.indexOf(b.pillar),
  );
}

/**
 * A pillar is "over target" when its actual share exceeds the target. With no
 * income the percent is null (R10) and nothing can be flagged — we never divide
 * by zero to invent an overrun.
 */
export function isPillarOverTarget(item: PillarAllocationReportPillarsItem): boolean {
  return item.actualPercent != null && item.actualPercent > item.targetPercent;
}

export function hasAnyOverrun(pillars: PillarAllocationReportPillarsItem[]): boolean {
  return pillars.some(isPillarOverTarget);
}

/** Shift an ISO month ("YYYY-MM") by a number of months, keeping the format. */
export function shiftMonth(isoMonth: string, delta: number): string {
  const [year, month] = isoMonth.split("-").map(Number);
  const date = new Date(year, month - 1 + delta, 1);
  const shiftedYear = date.getFullYear();
  const shiftedMonth = String(date.getMonth() + 1).padStart(2, "0");
  return `${shiftedYear}-${shiftedMonth}`;
}

/** Current month in YYYY-MM (local time). Used as the initial dashboard month. */
export function currentMonth(now: Date = new Date()): string {
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  return `${year}-${month}`;
}

export interface CategorySpend {
  categoryId: string;
  name: string;
  pillar: Pillar;
  amountCents: number;
}

/**
 * Resolve category display names and sort spends high-to-low. Categories the
 * lookup does not know about (e.g. removed) fall back to a stable placeholder
 * name so the row still renders.
 */
export function resolveCategorySpends(
  items: DashboardSummaryExpenseByCategoryItem[],
  categories: Category[],
  fallbackName: string,
): CategorySpend[] {
  const byId = new Map(categories.map((category) => [category.id, category]));
  return items
    .map((item) => ({
      categoryId: item.categoryId,
      name: byId.get(item.categoryId)?.name ?? fallbackName,
      pillar: item.pillar,
      amountCents: item.amountCents,
    }))
    .sort((a, b) => b.amountCents - a.amountCents);
}

/** Pillars that actually have spending in the breakdown, in canonical order. */
export function pillarsWithSpend(spends: CategorySpend[]): Pillar[] {
  const present = new Set(spends.map((spend) => spend.pillar));
  return PILLAR_ORDER.filter((pillar) => present.has(pillar));
}

export function sumCents(items: { amountCents: number }[]): number {
  return items.reduce((total, item) => total + item.amountCents, 0);
}

export interface Delta {
  cents: number;
  /** Percent change vs the previous value; null when there is nothing to compare. */
  percent: number | null;
}

/**
 * Month-over-month change. `previous` is null when the earlier month has no
 * data, in which case the percent is undefined (shown as "no comparison").
 */
export function computeDelta(current: number, previous: number | null | undefined): Delta {
  if (previous == null) {
    return { cents: current, percent: null };
  }
  const cents = current - previous;
  const percent = previous === 0 ? null : (cents / previous) * 100;
  return { cents, percent };
}
