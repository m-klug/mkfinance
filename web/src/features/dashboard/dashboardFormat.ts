import { Pillar } from "@/lib/api/generated/mKFinanceAPI.schemas";

const percentFormatter = new Intl.NumberFormat("pt-BR", {
  maximumFractionDigits: 1,
});

/** "24.5" → "24,5%". Used for allocation shares and month-over-month deltas. */
export function formatPercent(value: number): string {
  return `${percentFormatter.format(value)}%`;
}

/** Signed percent for comparisons: 12 → "+12%", -3.5 → "-3,5%". */
export function formatSignedPercent(value: number): string {
  const sign = value > 0 ? "+" : "";
  return `${sign}${percentFormatter.format(value)}%`;
}

// A fixed color per pillar so charts and legends stay consistent across the
// dashboard. Tuned to read on both light and dark themes.
export const PILLAR_COLORS: Record<Pillar, string> = {
  [Pillar.FIXED_COSTS]: "#2563eb",
  [Pillar.COMFORT]: "#0891b2",
  [Pillar.GOALS]: "#16a34a",
  [Pillar.PLEASURES]: "#db2777",
  [Pillar.FINANCIAL_FREEDOM]: "#7c3aed",
  [Pillar.KNOWLEDGE]: "#ca8a04",
};
