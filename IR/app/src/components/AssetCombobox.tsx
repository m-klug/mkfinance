import { useState } from "react"
import { Check, ChevronsUpDown } from "lucide-react"
import { cn } from "@/lib/utils"
import { Button } from "@/components/ui/button"
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command"
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover"
import type { Ativo } from "@/lib/types"

export function AssetCombobox({
  ativos,
  value,
  onChange,
  placeholder = "Buscar ativo...",
  allowClear = false,
  className,
}: {
  ativos: Ativo[]
  value: string | null
  onChange: (ticker: string | null) => void
  placeholder?: string
  allowClear?: boolean
  className?: string
}) {
  const [open, setOpen] = useState(false)
  const selecionado = ativos.find((a) => a.ticker === value)
  const br = ativos.filter((a) => a.mercado === "BR")
  const eua = ativos.filter((a) => a.mercado === "EUA")

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          variant="outline"
          role="combobox"
          aria-expanded={open}
          className={cn("w-full justify-between font-normal", className)}
        >
          {selecionado ? (
            <span className="flex items-center gap-2 truncate">
              <span className="font-medium tabular-nums">{selecionado.ticker}</span>
              <span className="truncate text-muted-foreground">{selecionado.nome}</span>
            </span>
          ) : (
            <span className="text-muted-foreground">{placeholder}</span>
          )}
          <ChevronsUpDown className="ml-2 size-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[320px] p-0">
        <Command>
          <CommandInput placeholder="Digite o ticker ou nome..." />
          <CommandList>
            <CommandEmpty>Nenhum ativo encontrado.</CommandEmpty>
            {allowClear && (
              <CommandGroup>
                <CommandItem
                  value="__todos__"
                  onSelect={() => {
                    onChange(null)
                    setOpen(false)
                  }}
                >
                  <Check className={cn("mr-2 size-4", value === null ? "opacity-100" : "opacity-0")} />
                  Todos os ativos
                </CommandItem>
              </CommandGroup>
            )}
            <CommandGroup heading="Carteira Brasil">
              {br.map((a) => (
                <CommandItem
                  key={a.ticker}
                  value={`${a.ticker} ${a.nome}`}
                  onSelect={() => {
                    onChange(a.ticker)
                    setOpen(false)
                  }}
                >
                  <Check className={cn("mr-2 size-4", value === a.ticker ? "opacity-100" : "opacity-0")} />
                  <span className="font-medium tabular-nums">{a.ticker}</span>
                  <span className="truncate text-muted-foreground">{a.nome}</span>
                </CommandItem>
              ))}
            </CommandGroup>
            <CommandGroup heading="Carteira Exterior">
              {eua.map((a) => (
                <CommandItem
                  key={a.ticker}
                  value={`${a.ticker} ${a.nome}`}
                  onSelect={() => {
                    onChange(a.ticker)
                    setOpen(false)
                  }}
                >
                  <Check className={cn("mr-2 size-4", value === a.ticker ? "opacity-100" : "opacity-0")} />
                  <span className="font-medium tabular-nums">{a.ticker}</span>
                  <span className="truncate text-muted-foreground">{a.nome}</span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  )
}
