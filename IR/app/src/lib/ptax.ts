// Mock de cotação PTAX (Banco Central) para conversão de ordens em ativos dos EUA.
// Gera taxas determinísticas por data (mesma data => mesma taxa), com fallback
// para o próximo dia útil quando a data cai em fim de semana/feriado (regra da Receita Federal).

const FERIADOS_FIXOS = new Set([
  "01-01", // Confraternização Universal
  "04-21", // Tiradentes
  "05-01", // Dia do Trabalho
  "09-07", // Independência
  "10-12", // Nossa Senhora Aparecida
  "11-02", // Finados
  "11-15", // Proclamação da República
  "11-20", // Consciência Negra
  "12-25", // Natal
])

// Cotações fixas para reproduzir exatamente os exemplos do enunciado.
const OVERRIDES: Record<string, { compra: number; venda: number }> = {
  "2025-01-10": { compra: 5.08, venda: 5.1 },
  "2025-06-20": { compra: 4.95, venda: 4.97 },
}

function pad(n: number) {
  return String(n).padStart(2, "0")
}

function toISO(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function isDiaUtil(d: Date) {
  const dow = d.getDay()
  if (dow === 0 || dow === 6) return false
  const mmdd = `${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
  return !FERIADOS_FIXOS.has(mmdd)
}

function hash(str: string): number {
  let h = 5381
  for (let i = 0; i < str.length; i++) {
    h = (h * 33) ^ str.charCodeAt(i)
  }
  return Math.abs(h >>> 0)
}

function baseRate(iso: string): number {
  // Deriva uma taxa "realista" e determinística a partir da data, entre ~4.85 e ~5.35.
  const h = hash(iso)
  const noise = (h % 1000) / 1000 // 0..1
  const t = new Date(iso).getTime() / (1000 * 60 * 60 * 24)
  const onda = Math.sin(t / 45) * 0.15
  return 5.05 + onda + (noise - 0.5) * 0.2
}

export interface CotacaoPtax {
  taxa: number
  dataCotacao: string
  dataSolicitada: string
  fallback: boolean
}

/**
 * Retorna a cotação PTAX a ser usada para converter uma ordem em USD.
 * Compras usam PTAX de venda; vendas/amortizações/bonificações usam PTAX de compra.
 */
export function getPtax(dataISO: string, tipo: "compra" | "venda"): CotacaoPtax {
  let d = new Date(`${dataISO}T12:00:00`)
  let deslocado = 0
  while (!isDiaUtil(d) && deslocado < 3) {
    d = new Date(d.getTime() + 24 * 60 * 60 * 1000)
    deslocado++
  }
  const dataCotacao = toISO(d)

  const override = OVERRIDES[dataCotacao]
  if (override) {
    return {
      taxa: override[tipo],
      dataCotacao,
      dataSolicitada: dataISO,
      fallback: deslocado > 0,
    }
  }

  const base = baseRate(dataCotacao)
  const spread = 0.02
  const taxa = tipo === "venda" ? base + spread / 2 : base - spread / 2

  return {
    taxa: Math.round(taxa * 10000) / 10000,
    dataCotacao,
    dataSolicitada: dataISO,
    fallback: deslocado > 0,
  }
}
