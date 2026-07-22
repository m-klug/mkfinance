import { useMemo, useState } from "react"
import { ArrowRight, CalendarClock, Wallet2 } from "lucide-react"
import { useAppStore } from "@/store/useAppStore"
import { computePosicao } from "@/lib/calc"
import { formatBRL, formatDateBR, formatNumber, formatUSD } from "@/lib/format"
import { AssetCombobox } from "@/components/AssetCombobox"
import { Card, CardContent, CardHeader } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import type { Ativo, PosicaoAtivo } from "@/lib/types"

function AssetCard({
  ativo,
  posHoje,
  posRef,
  dataReferencia,
}: {
  ativo: Ativo
  posHoje: PosicaoAtivo
  posRef: PosicaoAtivo
  dataReferencia: string
}) {
  const ehEUA = ativo.mercado === "EUA"

  return (
    <Card className="gap-4 py-5">
      <CardHeader className="px-5">
        <div className="flex items-start justify-between gap-2">
          <div>
            <div className="flex items-center gap-2">
              <p className="font-mono text-base font-semibold tracking-tight">{ativo.ticker}</p>
              <Badge variant="secondary" className="text-[10px]">
                {ativo.categoria === "FII" ? "FII" : ativo.categoria === "ACAO" ? "Ação" : ativo.categoria.replace("_EUA", "")}
              </Badge>
            </div>
            <p className="truncate text-xs text-muted-foreground">{ativo.nome}</p>
          </div>
          <Badge className={ehEUA ? "bg-indigo-600/90" : "bg-emerald-600/90"}>{ehEUA ? "Exterior" : "Brasil"}</Badge>
        </div>
      </CardHeader>
      <CardContent className="space-y-4 px-5">
        <div className="grid grid-cols-2 gap-4 text-xs font-medium text-muted-foreground">
          <div className="flex items-center gap-1.5">
            <span className="size-1.5 rounded-full bg-primary" /> Hoje
          </div>
          <div className="flex items-center gap-1.5 border-l border-border pl-4">
            <CalendarClock className="size-3.5" /> {formatDateBR(dataReferencia)}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <p className="text-2xl font-semibold tabular-nums">{formatNumber(posHoje.quantidade)}</p>
          <p className="border-l border-border pl-4 text-2xl font-semibold tabular-nums text-muted-foreground">
            {formatNumber(posRef.quantidade)}
          </p>
        </div>
        <p className="-mt-3 text-[11px] tracking-wide text-muted-foreground uppercase">Quantidade total</p>

        <div className="grid grid-cols-2 gap-4 border-t border-border pt-3">
          <div>
            <p className="text-[11px] tracking-wide text-muted-foreground uppercase">Preço médio</p>
            {ehEUA ? (
              <>
                <p className="tabular-nums font-medium">{formatUSD(posHoje.pm)}</p>
                <p className="text-xs tabular-nums text-muted-foreground">{formatBRL(posHoje.pmBRL ?? 0)}</p>
              </>
            ) : (
              <p className="tabular-nums font-medium">{formatBRL(posHoje.pm)}</p>
            )}
          </div>
          <div className="border-l border-border pl-4">
            <p className="text-[11px] tracking-wide text-muted-foreground uppercase">Preço médio</p>
            {ehEUA ? (
              <>
                <p className="tabular-nums font-medium text-muted-foreground">{formatUSD(posRef.pm)}</p>
                <p className="text-xs tabular-nums text-muted-foreground">{formatBRL(posRef.pmBRL ?? 0)}</p>
              </>
            ) : (
              <p className="tabular-nums font-medium text-muted-foreground">{formatBRL(posRef.pm)}</p>
            )}
          </div>
        </div>

        <div className="grid grid-cols-2 gap-4 border-t border-border pt-3">
          <div>
            <p className="text-[11px] tracking-wide text-muted-foreground uppercase">Custo total</p>
            {ehEUA ? (
              <>
                <p className="tabular-nums font-medium">{formatUSD(posHoje.custoTotal)}</p>
                <p className="text-xs tabular-nums text-muted-foreground">{formatBRL(posHoje.custoTotalBRL ?? 0)}</p>
              </>
            ) : (
              <p className="tabular-nums font-medium">{formatBRL(posHoje.custoTotal)}</p>
            )}
          </div>
          <div className="border-l border-border pl-4">
            <p className="text-[11px] tracking-wide text-muted-foreground uppercase">Custo total</p>
            {ehEUA ? (
              <>
                <p className="tabular-nums font-medium text-muted-foreground">{formatUSD(posRef.custoTotal)}</p>
                <p className="text-xs tabular-nums text-muted-foreground">{formatBRL(posRef.custoTotalBRL ?? 0)}</p>
              </>
            ) : (
              <p className="tabular-nums font-medium text-muted-foreground">{formatBRL(posRef.custoTotal)}</p>
            )}
          </div>
        </div>
      </CardContent>
    </Card>
  )
}

export function Dashboard() {
  const ativos = useAppStore((s) => s.ativos)
  const ordens = useAppStore((s) => s.ordens)
  const dataReferencia = useAppStore((s) => s.dataReferencia)
  const setDataReferencia = useAppStore((s) => s.setDataReferencia)
  const [filtro, setFiltro] = useState<string | null>(null)

  const posicoes = useMemo(() => {
    return ativos.map((ativo) => ({
      ativo,
      hoje: computePosicao(ordens, ativo),
      ref: computePosicao(ordens, ativo, dataReferencia),
    }))
  }, [ativos, ordens, dataReferencia])

  const visiveis = filtro ? posicoes.filter((p) => p.ativo.ticker === filtro) : posicoes
  const br = visiveis.filter((p) => p.ativo.mercado === "BR")
  const eua = visiveis.filter((p) => p.ativo.mercado === "EUA")

  const totalBrasil = posicoes.filter((p) => p.ativo.mercado === "BR").reduce((acc, p) => acc + p.hoje.custoTotal, 0)
  const totalExterior = posicoes
    .filter((p) => p.ativo.mercado === "EUA")
    .reduce((acc, p) => acc + (p.hoje.custoTotalBRL ?? 0), 0)

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Painel de Posição</h1>
          <p className="text-sm text-muted-foreground">
            Quantidade, preço médio e custo de aquisição por ativo, hoje e na data de referência do IRPF.
          </p>
        </div>
        <div className="flex flex-wrap items-end gap-3">
          <div className="w-64">
            <Label className="mb-1.5 text-xs text-muted-foreground">Ativo</Label>
            <AssetCombobox ativos={ativos} value={filtro} onChange={setFiltro} allowClear placeholder="Todos os ativos" />
          </div>
          <div>
            <Label htmlFor="data-ref" className="mb-1.5 text-xs text-muted-foreground">
              Data de referência
            </Label>
            <Input
              id="data-ref"
              type="date"
              value={dataReferencia}
              onChange={(e) => setDataReferencia(e.target.value)}
              className="w-40"
            />
          </div>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Card className="py-5">
          <CardContent className="flex items-center justify-between px-5">
            <div>
              <p className="text-xs tracking-wide text-muted-foreground uppercase">Custo total · Carteira Brasil</p>
              <p className="text-2xl font-semibold tabular-nums">{formatBRL(totalBrasil)}</p>
            </div>
            <div className="flex size-10 items-center justify-center rounded-full bg-emerald-600/10 text-emerald-600">
              <Wallet2 className="size-5" />
            </div>
          </CardContent>
        </Card>
        <Card className="py-5">
          <CardContent className="flex items-center justify-between px-5">
            <div>
              <p className="text-xs tracking-wide text-muted-foreground uppercase">Custo total · Carteira Exterior</p>
              <p className="text-2xl font-semibold tabular-nums">{formatBRL(totalExterior)}</p>
            </div>
            <div className="flex size-10 items-center justify-center rounded-full bg-indigo-600/10 text-indigo-600">
              <Wallet2 className="size-5" />
            </div>
          </CardContent>
        </Card>
      </div>

      {br.length > 0 && (
        <section className="space-y-3">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-muted-foreground">
            <span className="size-2 rounded-full bg-emerald-600" /> Carteira Brasil
          </h2>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {br.map((p) => (
              <AssetCard key={p.ativo.ticker} ativo={p.ativo} posHoje={p.hoje} posRef={p.ref} dataReferencia={dataReferencia} />
            ))}
          </div>
        </section>
      )}

      {eua.length > 0 && (
        <section className="space-y-3">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-muted-foreground">
            <span className="size-2 rounded-full bg-indigo-600" /> Carteira Exterior
            <ArrowRight className="size-3.5" />
            <span className="text-xs font-normal">valores em USD e BRL</span>
          </h2>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {eua.map((p) => (
              <AssetCard key={p.ativo.ticker} ativo={p.ativo} posHoje={p.hoje} posRef={p.ref} dataReferencia={dataReferencia} />
            ))}
          </div>
        </section>
      )}
    </div>
  )
}
