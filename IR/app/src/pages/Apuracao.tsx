import { useMemo, useState } from "react"
import { Calculator, Receipt, TrendingDown, TrendingUp } from "lucide-react"
import { useAppStore } from "@/store/useAppStore"
import { apurarVendas } from "@/lib/calc"
import { formatBRL, formatDateBR, formatNumber, formatPercent, mesAnoLabel } from "@/lib/format"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { cn } from "@/lib/utils"

export function Apuracao() {
  const ativos = useAppStore((s) => s.ativos)
  const ordens = useAppStore((s) => s.ordens)

  const anosDisponiveis = useMemo(() => {
    const anos = new Set(ordens.filter((o) => o.tipo === "VENDA").map((o) => o.data.slice(0, 4)))
    return Array.from(anos).sort().reverse()
  }, [ordens])

  const [ano, setAno] = useState(anosDisponiveis[0] ?? String(new Date().getFullYear()))

  const vendas = useMemo(
    () => apurarVendas(ordens, ativos, `${ano}-01-01`, `${ano}-12-31`),
    [ordens, ativos, ano]
  )

  const grupos = useMemo(() => {
    const mapa = new Map<string, typeof vendas>()
    for (const v of vendas) {
      const chave = v.linha.data.slice(0, 7)
      if (!mapa.has(chave)) mapa.set(chave, [])
      mapa.get(chave)!.push(v)
    }
    return Array.from(mapa.entries()).sort((a, b) => a[0].localeCompare(b[0]))
  }, [vendas])

  const ganhoTotal = vendas.reduce((acc, v) => acc + (v.ativo.mercado === "EUA" ? v.linha.ganhoNaVendaBRL ?? 0 : v.linha.ganhoNaVenda ?? 0), 0)
  const impostoTotal = vendas.reduce((acc, v) => acc + (v.linha.imposto ?? 0), 0)
  const operacoesComPrejuizo = vendas.filter((v) => (v.linha.ganhoNaVendaBRL ?? v.linha.ganhoNaVenda ?? 0) < 0).length

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Apuração de Ganho de Capital</h1>
          <p className="text-sm text-muted-foreground">Vendas do período, agrupadas por mês, com lucro/prejuízo e imposto devido.</p>
        </div>
        <div className="w-32">
          <Select value={ano} onValueChange={setAno}>
            <SelectTrigger className="w-full">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(anosDisponiveis.length ? anosDisponiveis : [ano]).map((a) => (
                <SelectItem key={a} value={a}>
                  {a}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card className="py-5">
          <CardContent className="flex items-center justify-between px-5">
            <div>
              <p className="text-xs tracking-wide text-muted-foreground uppercase">Ganho apurado no ano</p>
              <p className={cn("text-2xl font-semibold tabular-nums", ganhoTotal >= 0 ? "text-emerald-600" : "text-rose-600")}>
                {formatBRL(ganhoTotal)}
              </p>
            </div>
            <div
              className={cn(
                "flex size-10 items-center justify-center rounded-full",
                ganhoTotal >= 0 ? "bg-emerald-600/10 text-emerald-600" : "bg-rose-600/10 text-rose-600"
              )}
            >
              {ganhoTotal >= 0 ? <TrendingUp className="size-5" /> : <TrendingDown className="size-5" />}
            </div>
          </CardContent>
        </Card>
        <Card className="py-5">
          <CardContent className="flex items-center justify-between px-5">
            <div>
              <p className="text-xs tracking-wide text-muted-foreground uppercase">Imposto devido (GCAP)</p>
              <p className="text-2xl font-semibold tabular-nums">{formatBRL(impostoTotal)}</p>
            </div>
            <div className="flex size-10 items-center justify-center rounded-full bg-primary/10 text-primary">
              <Receipt className="size-5" />
            </div>
          </CardContent>
        </Card>
        <Card className="py-5">
          <CardContent className="flex items-center justify-between px-5">
            <div>
              <p className="text-xs tracking-wide text-muted-foreground uppercase">Operações no período</p>
              <p className="text-2xl font-semibold tabular-nums">{vendas.length}</p>
              <p className="text-xs text-muted-foreground">{operacoesComPrejuizo} com prejuízo</p>
            </div>
            <div className="flex size-10 items-center justify-center rounded-full bg-secondary text-secondary-foreground">
              <Calculator className="size-5" />
            </div>
          </CardContent>
        </Card>
      </div>

      {grupos.length === 0 && (
        <Card>
          <CardContent className="py-12 text-center text-muted-foreground">Nenhuma venda registrada em {ano}.</CardContent>
        </Card>
      )}

      <div className="space-y-4">
        {grupos.map(([mes, itens]) => {
          const ganhoMes = itens.reduce(
            (acc, v) => acc + (v.ativo.mercado === "EUA" ? v.linha.ganhoNaVendaBRL ?? 0 : v.linha.ganhoNaVenda ?? 0),
            0
          )
          const impostoMes = itens.reduce((acc, v) => acc + (v.linha.imposto ?? 0), 0)

          return (
            <Card key={mes} className="py-0">
              <CardHeader className="flex flex-row flex-wrap items-center justify-between gap-2 border-b border-border py-4">
                <CardTitle className="text-base font-semibold">{mesAnoLabel(`${mes}-01`)}</CardTitle>
                <div className="flex items-center gap-4 text-sm">
                  <span className="text-muted-foreground">
                    Resultado: <span className={cn("font-medium tabular-nums", ganhoMes >= 0 ? "text-emerald-600" : "text-rose-600")}>{formatBRL(ganhoMes)}</span>
                  </span>
                  <span className="text-muted-foreground">
                    Imposto: <span className="font-medium tabular-nums text-foreground">{formatBRL(impostoMes)}</span>
                  </span>
                </div>
              </CardHeader>
              <CardContent className="px-0">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Ativo</TableHead>
                      <TableHead>Mercado</TableHead>
                      <TableHead>Data</TableHead>
                      <TableHead className="text-right">Quantidade</TableHead>
                      <TableHead className="text-right">Alíquota</TableHead>
                      <TableHead className="text-right">Resultado</TableHead>
                      <TableHead className="text-right">Imposto</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {itens.map(({ linha, ativo }) => {
                      const ganho = ativo.mercado === "EUA" ? linha.ganhoNaVendaBRL ?? 0 : linha.ganhoNaVenda ?? 0
                      const lucro = ganho > 0
                      return (
                        <TableRow key={linha.id}>
                          <TableCell className="font-mono font-medium">{ativo.ticker}</TableCell>
                          <TableCell>
                            <Badge variant="outline" className={ativo.mercado === "EUA" ? "text-indigo-600" : "text-emerald-600"}>
                              {ativo.mercado === "EUA" ? "Exterior" : "Brasil"}
                            </Badge>
                          </TableCell>
                          <TableCell className="tabular-nums">{formatDateBR(linha.data)}</TableCell>
                          <TableCell className="text-right tabular-nums">{formatNumber(linha.quantidade)}</TableCell>
                          <TableCell className="text-right tabular-nums text-muted-foreground">
                            {linha.aliquota ? formatPercent(linha.aliquota) : "—"}
                          </TableCell>
                          <TableCell
                            className={cn(
                              "text-right font-medium tabular-nums",
                              lucro ? "text-emerald-600 dark:text-emerald-400" : "text-rose-600 dark:text-rose-400"
                            )}
                          >
                            {formatBRL(ganho)}
                          </TableCell>
                          <TableCell className="text-right">
                            {linha.imposto !== undefined ? (
                              <Badge className="border-rose-200 bg-rose-50 text-rose-700 tabular-nums dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-400" variant="outline">
                                {formatBRL(linha.imposto)}
                              </Badge>
                            ) : (
                              <Badge variant="outline" className="text-muted-foreground">
                                sem imposto
                              </Badge>
                            )}
                          </TableCell>
                        </TableRow>
                      )
                    })}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )
        })}
      </div>
    </div>
  )
}
