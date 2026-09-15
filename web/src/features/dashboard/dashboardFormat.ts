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

// Fixed color per pillar (design_handoff_mk_finance/README.md — same in both
// themes), so charts, legends and target bars stay consistent everywhere.
export const PILLAR_COLORS: Record<Pillar, string> = {
  [Pillar.FIXED_COSTS]: "#2FBF8F",
  [Pillar.COMFORT]: "#86CFB6",
  [Pillar.GOALS]: "#B9E3D4",
  [Pillar.PLEASURES]: "#F0A68F",
  [Pillar.FINANCIAL_FREEDOM]: "#5B6B7A",
  [Pillar.KNOWLEDGE]: "#CFD4D1",
};
