const percent = new Intl.NumberFormat("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** 13.65 -> "13,65%" */
export function formatPercent(value: number): string {
  return `${percent.format(value)}%`;
}

/** 13.65 -> "13,65", for an input's placeholder or value. */
export function formatDecimal(value: number): string {
  return percent.format(value);
}
