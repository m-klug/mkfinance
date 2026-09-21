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

// Fixed color per pillar (design_handoff_mk_finance/README.md §Regras de
// tematização #4). Financial Freedom and Knowledge shift hex in dark mode
// for legibility, so these reference the CSS custom properties (index.css)
// rather than hardcoding hex — charts, legends and target bars all follow
// the active theme automatically.
export const PILLAR_COLORS: Record<Pillar, string> = {
  [Pillar.FIXED_COSTS]: "var(--pillar-fixed-costs)",
  [Pillar.COMFORT]: "var(--pillar-comfort)",
  [Pillar.GOALS]: "var(--pillar-goals)",
  [Pillar.PLEASURES]: "var(--pillar-pleasures)",
  [Pillar.FINANCIAL_FREEDOM]: "var(--pillar-financial-freedom)",
  [Pillar.KNOWLEDGE]: "var(--pillar-knowledge)",
};
