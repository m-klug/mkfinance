export type Mercado = "BR" | "EUA"

export type CategoriaAtivo = "ACAO" | "FII" | "ETF" | "UNIT" | "STOCK" | "REIT" | "ETF_EUA"

export type TipoOrdem =
  | "COMPRA"
  | "VENDA"
  | "DESDOBRAMENTO"
  | "GRUPAMENTO"
  | "AMORTIZACAO"
  | "BONIFICACAO"

export const TIPOS_ORDEM: { value: TipoOrdem; label: string }[] = [
  { value: "COMPRA", label: "Compra" },
  { value: "VENDA", label: "Venda" },
  { value: "DESDOBRAMENTO", label: "Desdobramento" },
  { value: "GRUPAMENTO", label: "Grupamento" },
  { value: "AMORTIZACAO", label: "Amortização" },
  { value: "BONIFICACAO", label: "Bonificação" },
]

export interface Ativo {
  ticker: string
  nome: string
  mercado: Mercado
  categoria: CategoriaAtivo
}

export interface Ordem {
  id: string
  ticker: string
  tipo: TipoOrdem
  data: string // ISO yyyy-mm-dd
  quantidade: number
  valorOrdem: number // moeda nativa do ativo (BRL para BR, USD para EUA)
  ptax?: {
    taxa: number
    tipo: "compra" | "venda"
    dataCotacao: string
    fallback: boolean
  }
}

export interface LinhaLedger extends Ordem {
  seq: number
  valorPorAcao: number
  quantidadeAcumulada: number
  pmAcumulado: number // moeda nativa
  pmAcumuladoBRL?: number // apenas EUA
  valorOrdemBRL?: number // apenas EUA
  custoTotalAcumulado: number // moeda nativa
  custoTotalAcumuladoBRL?: number
  ganhoNaVenda?: number // moeda nativa
  ganhoNaVendaBRL?: number
  aliquota?: number
  imposto?: number // sempre BRL
}

export interface PosicaoAtivo {
  ticker: string
  ativo: Ativo
  quantidade: number
  pm: number
  custoTotal: number
  pmBRL?: number
  custoTotalBRL?: number
}
