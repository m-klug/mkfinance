import type { Ativo, Ordem } from "./types"

export const ATIVOS: Ativo[] = [
  { ticker: "ITSA4", nome: "Itaúsa PN", mercado: "BR", categoria: "ACAO" },
  { ticker: "PETR4", nome: "Petrobras PN", mercado: "BR", categoria: "ACAO" },
  { ticker: "MXRF11", nome: "Maxi Renda FII", mercado: "BR", categoria: "FII" },
  { ticker: "HGLG11", nome: "CSHG Logística FII", mercado: "BR", categoria: "FII" },
  { ticker: "BOVA11", nome: "iShares Ibovespa ETF", mercado: "BR", categoria: "ETF" },
  { ticker: "AAPL", nome: "Apple Inc.", mercado: "EUA", categoria: "STOCK" },
  { ticker: "O", nome: "Realty Income Corp.", mercado: "EUA", categoria: "REIT" },
  { ticker: "VOO", nome: "Vanguard S&P 500 ETF", mercado: "EUA", categoria: "ETF_EUA" },
]

let seq = 1
function ordem(o: Omit<Ordem, "id">): Ordem {
  return { ...o, id: `o${seq++}` }
}

export const ORDENS_SEED: Ordem[] = [
  // ITSA4 — exemplo do enunciado
  ordem({ ticker: "ITSA4", tipo: "COMPRA", data: "2025-01-15", quantidade: 100, valorOrdem: 950 }),
  ordem({ ticker: "ITSA4", tipo: "COMPRA", data: "2025-03-10", quantidade: 100, valorOrdem: 1050 }),
  ordem({ ticker: "ITSA4", tipo: "VENDA", data: "2025-06-05", quantidade: 50, valorOrdem: 600 }),
  ordem({ ticker: "ITSA4", tipo: "COMPRA", data: "2026-02-10", quantidade: 50, valorOrdem: 550 }),

  // PETR4 — inclui bonificação (custo zero dilui o PM)
  ordem({ ticker: "PETR4", tipo: "COMPRA", data: "2025-04-02", quantidade: 200, valorOrdem: 6400 }),
  ordem({ ticker: "PETR4", tipo: "COMPRA", data: "2025-09-12", quantidade: 100, valorOrdem: 3550 }),
  ordem({ ticker: "PETR4", tipo: "BONIFICACAO", data: "2025-11-03", quantidade: 10, valorOrdem: 0 }),

  // MXRF11 — exemplo do enunciado (amortização reduz o PM)
  ordem({ ticker: "MXRF11", tipo: "COMPRA", data: "2025-02-01", quantidade: 200, valorOrdem: 2000 }),
  ordem({ ticker: "MXRF11", tipo: "AMORTIZACAO", data: "2025-03-01", quantidade: 200, valorOrdem: 40 }),
  ordem({ ticker: "MXRF11", tipo: "VENDA", data: "2026-03-15", quantidade: 50, valorOrdem: 575 }),

  // HGLG11 — inclui grupamento (custo total preservado)
  ordem({ ticker: "HGLG11", tipo: "COMPRA", data: "2025-05-20", quantidade: 80, valorOrdem: 13200 }),
  ordem({ ticker: "HGLG11", tipo: "GRUPAMENTO", data: "2025-10-01", quantidade: 8, valorOrdem: 0 }),

  // BOVA11 — inclui desdobramento (custo total preservado)
  ordem({ ticker: "BOVA11", tipo: "COMPRA", data: "2025-02-15", quantidade: 50, valorOrdem: 4900 }),
  ordem({ ticker: "BOVA11", tipo: "DESDOBRAMENTO", data: "2025-08-01", quantidade: 50, valorOrdem: 0 }),

  // AAPL — exemplo do enunciado (conversão via PTAX)
  ordem({ ticker: "AAPL", tipo: "COMPRA", data: "2025-01-10", quantidade: 10, valorOrdem: 1500 }),
  ordem({ ticker: "AAPL", tipo: "VENDA", data: "2025-06-20", quantidade: 5, valorOrdem: 900 }),
  ordem({ ticker: "AAPL", tipo: "COMPRA", data: "2026-02-05", quantidade: 5, valorOrdem: 850 }),

  // O — REIT com posição simples
  ordem({ ticker: "O", tipo: "COMPRA", data: "2025-03-05", quantidade: 20, valorOrdem: 1100 }),

  // VOO — ETF EUA com posição simples
  ordem({ ticker: "VOO", tipo: "COMPRA", data: "2025-07-01", quantidade: 3, valorOrdem: 1260 }),
]

export const DATA_REFERENCIA_PADRAO = "2025-12-31"
