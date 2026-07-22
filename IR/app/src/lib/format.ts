export function formatBRL(v: number): string {
  return v.toLocaleString("pt-BR", { style: "currency", currency: "BRL" })
}

export function formatUSD(v: number): string {
  return v.toLocaleString("en-US", { style: "currency", currency: "USD" })
}

export function formatNumber(v: number, casas = 0): string {
  return v.toLocaleString("pt-BR", { minimumFractionDigits: casas, maximumFractionDigits: casas })
}

export function formatDateBR(iso: string): string {
  const [y, m, d] = iso.split("-")
  return `${d}/${m}/${y}`
}

export function formatPercent(v: number): string {
  return `${(v * 100).toLocaleString("pt-BR", { maximumFractionDigits: 0 })}%`
}

export const MESES_PT = [
  "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
  "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro",
]

export function mesAnoLabel(iso: string): string {
  const [y, m] = iso.split("-")
  return `${MESES_PT[Number(m) - 1]} de ${y}`
}
