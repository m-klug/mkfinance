import { useMemo, useState } from "react"
import { useAppStore } from "@/store/useAppStore"
import { buildLedger } from "@/lib/calc"
import { formatBRL, formatDateBR, formatNumber, formatUSD } from "@/lib/format"
import { AssetCombobox } from "@/components/AssetCombobox"
import { OrderTypeBadge } from "@/components/OrderTypeBadge"
import { Badge } from "@/components/ui/badge"
import { Card, CardContent } from "@/components/ui/card"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { cn } from "@/lib/utils"

export function Historico() {
  const ativos = useAppStore((s) => s.ativos)
  const ordens = useAppStore((s) => s.ordens)
  const [ticker, setTicker] = useState<string | null>(ativos[0]?.ticker ?? null)

  const ativo = ativos.find((a) => a.ticker === ticker) ?? ativos[0]
  const ehEUA = ativo?.mercado === "EUA"

  const ledger = useMemo(() => (ativo ? buildLedger(ordens, ativo) : []), [ativo, ordens])

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Histórico de Ordens</h1>
          <p className="text-sm text-muted-foreground">
            Evolução ordem a ordem da quantidade acumulada, preço médio e imposto sobre ganho de capital.
          </p>
        </div>
        <div className="w-64">
          <AssetCombobox ativos={ativos} value={ticker} onChange={setTicker} placeholder="Selecione o ativo..." />
        </div>
      </div>

      {ativo && (
        <div className="flex flex-wrap items-center gap-2">
          {ativos
            .filter((a) => a.mercado === ativo.mercado)
            .map((a) => (
              <button
                key={a.ticker}
                onClick={() => setTicker(a.ticker)}
                className={cn(
                  "rounded-full border px-3 py-1 text-xs font-medium transition-colors",
                  a.ticker === ativo.ticker
                    ? "border-primary bg-primary text-primary-foreground"
                    : "border-border text-muted-foreground hover:bg-muted"
                )}
              >
                {a.ticker}
              </button>
            ))}
        </div>
      )}

      <Card className="py-0">
        <CardContent className="px-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-10">#</TableHead>
                <TableHead>Ativo</TableHead>
                <TableHead>Ordem</TableHead>
                <TableHead>Data</TableHead>
                <TableHead className="text-right">Quantidade</TableHead>
                {ehEUA ? (
                  <>
                    <TableHead className="text-right">Valor da Ordem (US$)</TableHead>
                    <TableHead className="text-right">Valor da Ordem (R$)</TableHead>
                    <TableHead className="text-right">Valor/Ação (US$)</TableHead>
                    <TableHead className="text-right">Valor/Ação (R$)</TableHead>
                  </>
                ) : (
                  <>
                    <TableHead className="text-right">Valor da Ordem</TableHead>
                    <TableHead className="text-right">Valor por Ação</TableHead>
                  </>
                )}
                <TableHead className="text-right">Qtd. Acumulada</TableHead>
                {ehEUA ? (
                  <>
                    <TableHead className="text-right">PM Acum. (US$)</TableHead>
                    <TableHead className="text-right">PM Acum. (R$)</TableHead>
                    <TableHead className="text-right">Ganho na Venda (US$)</TableHead>
                    <TableHead className="text-right">Ganho na Venda (R$)</TableHead>
                  </>
                ) : (
                  <>
                    <TableHead className="text-right">PM Acumulado</TableHead>
                    <TableHead className="text-right">Ganho na Venda</TableHead>
                  </>
                )}
                <TableHead className="text-right">Imposto (GCAP)</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {ledger.map((l) => {
                const venda = l.tipo === "VENDA"
                const ganhoBRL = ehEUA ? l.ganhoNaVendaBRL : l.ganhoNaVenda
                const lucro = venda && (ganhoBRL ?? 0) > 0
                const prejuizo = venda && (ganhoBRL ?? 0) < 0

                return (
                  <TableRow key={l.id} className={cn(lucro && "bg-emerald-50/60 dark:bg-emerald-500/5")}>
                    <TableCell className="tabular-nums text-muted-foreground">{l.seq}</TableCell>
                    <TableCell className="font-mono font-medium">{l.ticker}</TableCell>
                    <TableCell>
                      <OrderTypeBadge tipo={l.tipo} />
                    </TableCell>
                    <TableCell className="tabular-nums">{formatDateBR(l.data)}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatNumber(l.quantidade)}</TableCell>

                    {ehEUA ? (
                      <>
                        <TableCell className="text-right tabular-nums">{formatUSD(l.valorOrdem)}</TableCell>
                        <TableCell className="text-right tabular-nums text-muted-foreground">
                          {formatBRL(l.valorOrdemBRL ?? 0)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">{formatUSD(l.valorPorAcao)}</TableCell>
                        <TableCell className="text-right tabular-nums text-muted-foreground">
                          {formatBRL((l.valorOrdemBRL ?? 0) / (l.quantidade || 1))}
                        </TableCell>
                      </>
                    ) : (
                      <>
                        <TableCell className="text-right tabular-nums">{formatBRL(l.valorOrdem)}</TableCell>
                        <TableCell className="text-right tabular-nums">{formatBRL(l.valorPorAcao)}</TableCell>
                      </>
                    )}

                    <TableCell className="text-right font-medium tabular-nums">{formatNumber(l.quantidadeAcumulada)}</TableCell>

                    {ehEUA ? (
                      <>
                        <TableCell className="text-right tabular-nums">{formatUSD(l.pmAcumulado)}</TableCell>
                        <TableCell className="text-right tabular-nums text-muted-foreground">
                          {formatBRL(l.pmAcumuladoBRL ?? 0)}
                        </TableCell>
                        <TableCell className="text-right tabular-nums">
                          {l.ganhoNaVenda !== undefined ? formatUSD(l.ganhoNaVenda) : "—"}
                        </TableCell>
                        <TableCell
                          className={cn(
                            "text-right tabular-nums",
                            lucro && "font-medium text-emerald-600 dark:text-emerald-400",
                            prejuizo && "text-rose-600 dark:text-rose-400"
                          )}
                        >
                          {l.ganhoNaVendaBRL !== undefined ? formatBRL(l.ganhoNaVendaBRL) : "—"}
                        </TableCell>
                      </>
                    ) : (
                      <>
                        <TableCell className="text-right tabular-nums">{formatBRL(l.pmAcumulado)}</TableCell>
                        <TableCell
                          className={cn(
                            "text-right tabular-nums",
                            lucro && "font-medium text-emerald-600 dark:text-emerald-400",
                            prejuizo && "text-rose-600 dark:text-rose-400"
                          )}
                        >
                          {l.ganhoNaVenda !== undefined ? formatBRL(l.ganhoNaVenda) : "—"}
                        </TableCell>
                      </>
                    )}

                    <TableCell className="text-right">
                      {l.imposto !== undefined ? (
                        <Badge className="border-rose-200 bg-rose-50 text-rose-700 tabular-nums dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-400" variant="outline">
                          {formatBRL(l.imposto)}
                        </Badge>
                      ) : venda ? (
                        <Badge variant="outline" className="text-muted-foreground">
                          —
                        </Badge>
                      ) : (
                        <span className="text-muted-foreground">—</span>
                      )}
                    </TableCell>
                  </TableRow>
                )
              })}
              {ledger.length === 0 && (
                <TableRow>
                  <TableCell colSpan={12} className="py-10 text-center text-muted-foreground">
                    Nenhuma ordem lançada para este ativo.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  )
}
