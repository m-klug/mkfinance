import { getPtax } from "./ptax"
import type { Ativo, LinhaLedger, Ordem, PosicaoAtivo } from "./types"

export function aliquotaGcap(ativo: Ativo): number {
  if (ativo.mercado === "EUA") return 0.15
  if (ativo.categoria === "FII") return 0.2
  return 0.15 // Ações, ETFs, Units
}

function ptaxTipoParaOrdem(tipo: Ordem["tipo"]): "compra" | "venda" {
  return tipo === "COMPRA" ? "venda" : "compra"
}

/** Ordena ordens por data e, em empate, pela ordem de criação (id). */
function ordenarOrdens(ordens: Ordem[]): Ordem[] {
  return [...ordens].sort((a, b) => {
    if (a.data !== b.data) return a.data.localeCompare(b.data)
    return a.id.localeCompare(b.id)
  })
}

/**
 * Recalcula, ordem a ordem, a quantidade acumulada, o preço médio e o
 * ganho/imposto de cada operação de um ativo, seguindo as regras fiscais
 * descritas no enunciado.
 */
export function buildLedger(ordens: Ordem[], ativo: Ativo): LinhaLedger[] {
  const ehEUA = ativo.mercado === "EUA"
  const aliquota = aliquotaGcap(ativo)
  const ordenadas = ordenarOrdens(ordens.filter((o) => o.ticker === ativo.ticker))

  let qtd = 0
  let pm = 0
  let pmBRL = 0

  const linhas: LinhaLedger[] = []

  ordenadas.forEach((ordem, idx) => {
    const qtdAnterior = qtd
    const pmAnterior = pm
    const pmAnteriorBRL = pmBRL

    let ptax: LinhaLedger["ptax"] | undefined
    let valorOrdemBRL: number | undefined
    if (ehEUA) {
      const tipoPtax = ptaxTipoParaOrdem(ordem.tipo)
      const cot = getPtax(ordem.data, tipoPtax)
      ptax = { taxa: cot.taxa, tipo: tipoPtax, dataCotacao: cot.dataCotacao, fallback: cot.fallback }
      valorOrdemBRL = ordem.valorOrdem * cot.taxa
    }

    const valorPorAcao = ordem.quantidade !== 0 ? ordem.valorOrdem / ordem.quantidade : 0
    const valorPorAcaoBRL = ehEUA && ordem.quantidade !== 0 ? (valorOrdemBRL ?? 0) / ordem.quantidade : undefined

    let novaQtd = qtdAnterior
    let ganhoNaVenda: number | undefined
    let ganhoNaVendaBRL: number | undefined

    switch (ordem.tipo) {
      case "COMPRA":
        novaQtd = qtdAnterior + ordem.quantidade
        pm = qtdAnterior === 0 ? valorPorAcao : (pmAnterior * qtdAnterior + ordem.valorOrdem) / novaQtd
        if (ehEUA) {
          pmBRL = qtdAnterior === 0 ? (valorPorAcaoBRL ?? 0) : (pmAnteriorBRL * qtdAnterior + (valorOrdemBRL ?? 0)) / novaQtd
        }
        break
      case "BONIFICACAO":
        novaQtd = qtdAnterior + ordem.quantidade
        pm = novaQtd === 0 ? 0 : (pmAnterior * qtdAnterior + ordem.valorOrdem) / novaQtd
        if (ehEUA) {
          pmBRL = novaQtd === 0 ? 0 : (pmAnteriorBRL * qtdAnterior + (valorOrdemBRL ?? 0)) / novaQtd
        }
        break
      case "DESDOBRAMENTO":
        novaQtd = qtdAnterior + ordem.quantidade
        pm = novaQtd === 0 ? 0 : (pmAnterior * qtdAnterior) / novaQtd
        if (ehEUA) pmBRL = novaQtd === 0 ? 0 : (pmAnteriorBRL * qtdAnterior) / novaQtd
        break
      case "GRUPAMENTO":
        novaQtd = qtdAnterior - ordem.quantidade
        pm = novaQtd === 0 ? 0 : (pmAnterior * qtdAnterior) / novaQtd
        if (ehEUA) pmBRL = novaQtd === 0 ? 0 : (pmAnteriorBRL * qtdAnterior) / novaQtd
        break
      case "AMORTIZACAO": {
        novaQtd = qtdAnterior
        const base = ordem.quantidade || qtdAnterior || 1
        pm = pmAnterior - ordem.valorOrdem / base
        if (ehEUA) pmBRL = pmAnteriorBRL - (valorOrdemBRL ?? 0) / base
        break
      }
      case "VENDA":
        novaQtd = qtdAnterior - ordem.quantidade
        pm = pmAnterior
        if (ehEUA) pmBRL = pmAnteriorBRL
        ganhoNaVenda = ordem.quantidade * (valorPorAcao - pmAnterior)
        if (ehEUA) ganhoNaVendaBRL = ordem.quantidade * ((valorPorAcaoBRL ?? 0) - pmAnteriorBRL)
        break
    }

    qtd = novaQtd

    const ganhoTributavelBRL = ehEUA ? ganhoNaVendaBRL : ganhoNaVenda
    const temImposto = ordem.tipo === "VENDA" && (ganhoTributavelBRL ?? 0) > 0
    const imposto = temImposto ? (ganhoTributavelBRL as number) * aliquota : undefined

    linhas.push({
      ...ordem,
      seq: idx + 1,
      valorPorAcao,
      quantidadeAcumulada: qtd,
      pmAcumulado: pm,
      pmAcumuladoBRL: ehEUA ? pmBRL : undefined,
      valorOrdemBRL,
      custoTotalAcumulado: pm * qtd,
      custoTotalAcumuladoBRL: ehEUA ? pmBRL * qtd : undefined,
      ganhoNaVenda,
      ganhoNaVendaBRL,
      aliquota: ordem.tipo === "VENDA" ? aliquota : undefined,
      imposto,
      ptax,
    })
  })

  return linhas
}

/** Posição consolidada de um ativo, opcionalmente até uma data de corte (inclusive). */
export function computePosicao(ordens: Ordem[], ativo: Ativo, ateData?: string): PosicaoAtivo {
  const filtradas = ateData ? ordens.filter((o) => o.ticker === ativo.ticker && o.data <= ateData) : ordens.filter((o) => o.ticker === ativo.ticker)
  const ledger = buildLedger(filtradas, ativo)
  const ultima = ledger[ledger.length - 1]

  if (!ultima) {
    return {
      ticker: ativo.ticker,
      ativo,
      quantidade: 0,
      pm: 0,
      custoTotal: 0,
      pmBRL: ativo.mercado === "EUA" ? 0 : undefined,
      custoTotalBRL: ativo.mercado === "EUA" ? 0 : undefined,
    }
  }

  return {
    ticker: ativo.ticker,
    ativo,
    quantidade: ultima.quantidadeAcumulada,
    pm: ultima.pmAcumulado,
    custoTotal: ultima.custoTotalAcumulado,
    pmBRL: ultima.pmAcumuladoBRL,
    custoTotalBRL: ultima.custoTotalAcumuladoBRL,
  }
}

export interface VendaApurada {
  linha: LinhaLedger
  ativo: Ativo
}

/** Todas as vendas (com ganho ou prejuízo) de todos os ativos, dentro de um intervalo de datas. */
export function apurarVendas(ordens: Ordem[], ativos: Ativo[], inicio: string, fim: string): VendaApurada[] {
  const resultado: VendaApurada[] = []
  for (const ativo of ativos) {
    const ledger = buildLedger(ordens, ativo)
    for (const linha of ledger) {
      if (linha.tipo === "VENDA" && linha.data >= inicio && linha.data <= fim) {
        resultado.push({ linha, ativo })
      }
    }
  }
  return resultado.sort((a, b) => a.linha.data.localeCompare(b.linha.data))
}
