import { useMemo, useState } from "react"
import { AlertTriangle, ArrowRightLeft, CheckCircle2, Info } from "lucide-react"
import { toast } from "sonner"
import { useAppStore } from "@/store/useAppStore"
import { computePosicao } from "@/lib/calc"
import { getPtax } from "@/lib/ptax"
import { formatBRL, formatDateBR, formatNumber, formatUSD } from "@/lib/format"
import { AssetCombobox } from "@/components/AssetCombobox"
import { OrderTypeBadge } from "@/components/OrderTypeBadge"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { TIPOS_ORDEM, type TipoOrdem } from "@/lib/types"

function todayISO() {
  return new Date().toISOString().slice(0, 10)
}

export function NovaOrdem() {
  const ativos = useAppStore((s) => s.ativos)
  const ordens = useAppStore((s) => s.ordens)
  const addOrdem = useAppStore((s) => s.addOrdem)
  const getAtivo = useAppStore((s) => s.getAtivo)

  const [ticker, setTicker] = useState<string | null>(null)
  const [tipo, setTipo] = useState<TipoOrdem>("COMPRA")
  const [data, setData] = useState(todayISO())
  const [quantidade, setQuantidade] = useState("")
  const [valorOrdem, setValorOrdem] = useState("")
  const [erro, setErro] = useState<string | null>(null)

  const ativo = ticker ? getAtivo(ticker) : undefined
  const ehEUA = ativo?.mercado === "EUA"

  const qtd = Number(quantidade.replace(",", "."))
  const valor = Number(valorOrdem.replace(",", "."))

  const posicaoNaData = useMemo(() => {
    if (!ativo) return null
    return computePosicao(ordens, ativo, data)
  }, [ativo, ordens, data])

  const ptaxPreview = useMemo(() => {
    if (!ehEUA || !data) return null
    const tipoPtax = tipo === "COMPRA" ? "venda" : "compra"
    return { ...getPtax(data, tipoPtax), tipo: tipoPtax }
  }, [ehEUA, tipo, data])

  const valorBRLPreview = ptaxPreview && !Number.isNaN(valor) ? valor * ptaxPreview.taxa : null

  function resetCamposNumericos() {
    setQuantidade("")
    setValorOrdem("")
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setErro(null)

    if (!ativo) {
      setErro("Selecione um ativo válido da carteira.")
      return
    }
    if (!TIPOS_ORDEM.some((t) => t.value === tipo)) {
      setErro("Tipo de ordem inválido.")
      return
    }
    if (!data) {
      setErro("Informe a data da ordem.")
      return
    }
    if (!qtd || qtd <= 0 || Number.isNaN(qtd)) {
      setErro("Informe uma quantidade válida.")
      return
    }
    if (Number.isNaN(valor) || (valor === 0 && tipo !== "DESDOBRAMENTO" && tipo !== "GRUPAMENTO" && tipo !== "BONIFICACAO")) {
      setErro("Informe o valor da ordem.")
      return
    }
    if (tipo === "VENDA") {
      const disponivel = posicaoNaData?.quantidade ?? 0
      if (qtd > disponivel) {
        setErro(
          `Venda de ${formatNumber(qtd)} un. excede a quantidade acumulada disponível em ${formatDateBR(data)} (${formatNumber(disponivel)} un.).`
        )
        return
      }
    }

    addOrdem({ ticker: ativo.ticker, tipo, data, quantidade: qtd, valorOrdem: valor })
    toast.success(`${TIPOS_ORDEM.find((t) => t.value === tipo)?.label} de ${ativo.ticker} lançada com sucesso.`, {
      description: `${formatNumber(qtd)} un. em ${formatDateBR(data)}`,
    })
    resetCamposNumericos()
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Lançamento de Ordens</h1>
        <p className="text-sm text-muted-foreground">
          Registre compras, vendas e eventos corporativos. O preço médio e o imposto são recalculados automaticamente.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Nova ordem</CardTitle>
          <CardDescription>Todos os campos são obrigatórios.</CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-5">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1.5 sm:col-span-2">
                <Label>Ativo</Label>
                <AssetCombobox ativos={ativos} value={ticker} onChange={setTicker} placeholder="Selecione o ticker..." />
              </div>

              <div className="space-y-1.5">
                <Label>Tipo de ordem</Label>
                <Select value={tipo} onValueChange={(v) => setTipo(v as TipoOrdem)}>
                  <SelectTrigger className="w-full">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {TIPOS_ORDEM.map((t) => (
                      <SelectItem key={t.value} value={t.value}>
                        {t.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="data">Data</Label>
                <Input id="data" type="date" value={data} onChange={(e) => setData(e.target.value)} />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="quantidade">Quantidade</Label>
                <Input
                  id="quantidade"
                  inputMode="decimal"
                  placeholder="0"
                  value={quantidade}
                  onChange={(e) => setQuantidade(e.target.value)}
                  className="tabular-nums"
                />
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="valor">Valor da ordem {ehEUA ? "(USD)" : "(BRL)"}</Label>
                <div className="relative">
                  <span className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-sm text-muted-foreground">
                    {ehEUA ? "US$" : "R$"}
                  </span>
                  <Input
                    id="valor"
                    inputMode="decimal"
                    placeholder="0,00"
                    value={valorOrdem}
                    onChange={(e) => setValorOrdem(e.target.value)}
                    className="pl-10 tabular-nums"
                  />
                </div>
              </div>
            </div>

            {ativo && (
              <div className="flex flex-wrap items-center gap-2 rounded-md border border-border bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
                <Info className="size-3.5 shrink-0" />
                <span>
                  Posição em {formatDateBR(data)}:{" "}
                  <span className="font-medium text-foreground tabular-nums">{formatNumber(posicaoNaData?.quantidade ?? 0)} un.</span>{" "}
                  · PM{" "}
                  <span className="font-medium text-foreground tabular-nums">
                    {ehEUA ? formatUSD(posicaoNaData?.pm ?? 0) : formatBRL(posicaoNaData?.pm ?? 0)}
                  </span>
                </span>
              </div>
            )}

            {ehEUA && ptaxPreview && (
              <div className="space-y-2 rounded-md border border-indigo-200 bg-indigo-50 px-3 py-3 text-sm dark:border-indigo-500/20 dark:bg-indigo-500/10">
                <div className="flex items-center gap-2 font-medium text-indigo-700 dark:text-indigo-300">
                  <ArrowRightLeft className="size-4" />
                  Conversão automática via PTAX
                </div>
                <div className="grid grid-cols-2 gap-2 text-xs text-indigo-900/80 dark:text-indigo-200/80 sm:grid-cols-4">
                  <div>
                    <p className="uppercase tracking-wide opacity-70">Tipo de cotação</p>
                    <p className="font-medium capitalize">PTAX {ptaxPreview.tipo}</p>
                  </div>
                  <div>
                    <p className="uppercase tracking-wide opacity-70">Cotação usada</p>
                    <p className="font-medium tabular-nums">R$ {ptaxPreview.taxa.toFixed(4)}</p>
                  </div>
                  <div>
                    <p className="uppercase tracking-wide opacity-70">Data da cotação</p>
                    <p className="font-medium tabular-nums">
                      {formatDateBR(ptaxPreview.dataCotacao)}
                      {ptaxPreview.fallback && " *"}
                    </p>
                  </div>
                  <div>
                    <p className="uppercase tracking-wide opacity-70">Valor em BRL</p>
                    <p className="font-medium tabular-nums">{valorBRLPreview !== null ? formatBRL(valorBRLPreview) : "—"}</p>
                  </div>
                </div>
                {ptaxPreview.fallback && (
                  <p className="text-[11px] text-indigo-900/60 dark:text-indigo-200/60">
                    * Data solicitada caiu em fim de semana/feriado — usada a cotação do próximo dia útil.
                  </p>
                )}
              </div>
            )}

            {erro && (
              <div className="flex items-start gap-2 rounded-md border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                <AlertTriangle className="mt-0.5 size-4 shrink-0" />
                {erro}
              </div>
            )}

            <div className="flex items-center justify-between border-t border-border pt-4">
              <div className="flex items-center gap-2 text-xs text-muted-foreground">
                {ativo && (
                  <>
                    <OrderTypeBadge tipo={tipo} />
                    <span>será aplicada a {ativo.ticker}</span>
                  </>
                )}
              </div>
              <Button type="submit" className="gap-2">
                <CheckCircle2 className="size-4" />
                Lançar ordem
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
