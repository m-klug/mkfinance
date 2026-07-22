import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"
import type { TipoOrdem } from "@/lib/types"

const LABELS: Record<TipoOrdem, string> = {
  COMPRA: "Compra",
  VENDA: "Venda",
  DESDOBRAMENTO: "Desdobramento",
  GRUPAMENTO: "Grupamento",
  AMORTIZACAO: "Amortização",
  BONIFICACAO: "Bonificação",
}

const CLASSES: Record<TipoOrdem, string> = {
  COMPRA:
    "bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-400 dark:border-emerald-500/20",
  VENDA:
    "bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-500/10 dark:text-rose-400 dark:border-rose-500/20",
  DESDOBRAMENTO:
    "bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-500/10 dark:text-blue-400 dark:border-blue-500/20",
  GRUPAMENTO:
    "bg-blue-50 text-blue-700 border-blue-200 dark:bg-blue-500/10 dark:text-blue-400 dark:border-blue-500/20",
  AMORTIZACAO:
    "bg-violet-50 text-violet-700 border-violet-200 dark:bg-violet-500/10 dark:text-violet-400 dark:border-violet-500/20",
  BONIFICACAO:
    "bg-violet-50 text-violet-700 border-violet-200 dark:bg-violet-500/10 dark:text-violet-400 dark:border-violet-500/20",
}

export function OrderTypeBadge({ tipo, className }: { tipo: TipoOrdem; className?: string }) {
  return (
    <Badge variant="outline" className={cn(CLASSES[tipo], className)}>
      {LABELS[tipo]}
    </Badge>
  )
}
