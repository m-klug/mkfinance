import { NavLink, Outlet } from "react-router-dom"
import { LayoutDashboard, PlusCircle, History, Calculator, Moon, Sun, Wallet } from "lucide-react"
import { cn } from "@/lib/utils"
import { useTheme } from "@/hooks/useTheme"
import { Button } from "@/components/ui/button"

const NAV = [
  { to: "/", label: "Painel de Posição", icon: LayoutDashboard, end: true },
  { to: "/lancamento", label: "Lançamento de Ordens", icon: PlusCircle, end: false },
  { to: "/historico", label: "Histórico de Ordens", icon: History, end: false },
  { to: "/apuracao", label: "Apuração de Ganho de Capital", icon: Calculator, end: false },
]

export function AppShell() {
  const { theme, toggleTheme } = useTheme()

  return (
    <div className="flex min-h-svh w-full bg-background">
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-64 flex-col border-r border-sidebar-border bg-sidebar text-sidebar-foreground md:flex">
        <div className="flex h-16 items-center gap-2 px-5">
          <div className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Wallet className="size-4" />
          </div>
          <div className="leading-tight">
            <p className="text-sm font-semibold">Controle de Ativos</p>
            <p className="text-xs text-sidebar-foreground/50">Custo &amp; IR</p>
          </div>
        </div>
        <nav className="flex flex-1 flex-col gap-1 px-3 py-2">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-sidebar-accent text-sidebar-accent-foreground"
                    : "text-sidebar-foreground/70 hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground"
                )
              }
            >
              <item.icon className="size-4 shrink-0" />
              <span className="truncate">{item.label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-sidebar-border p-3">
          <Button
            variant="ghost"
            size="sm"
            className="w-full justify-start gap-3 text-sidebar-foreground/70 hover:bg-sidebar-accent/60 hover:text-sidebar-accent-foreground"
            onClick={toggleTheme}
          >
            {theme === "dark" ? <Sun className="size-4" /> : <Moon className="size-4" />}
            {theme === "dark" ? "Modo claro" : "Modo escuro"}
          </Button>
        </div>
      </aside>

      <div className="flex min-h-svh w-full flex-col md:pl-64">
        <header className="flex h-16 items-center justify-between border-b border-border bg-background/80 px-4 backdrop-blur md:hidden">
          <div className="flex items-center gap-2">
            <div className="flex size-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
              <Wallet className="size-4" />
            </div>
            <p className="text-sm font-semibold">Controle de Ativos</p>
          </div>
          <Button variant="ghost" size="icon" onClick={toggleTheme}>
            {theme === "dark" ? <Sun className="size-4" /> : <Moon className="size-4" />}
          </Button>
        </header>

        <nav className="flex gap-1 overflow-x-auto border-b border-border bg-background px-2 py-2 md:hidden">
          {NAV.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(
                  "flex shrink-0 items-center gap-2 rounded-md px-3 py-1.5 text-xs font-medium",
                  isActive ? "bg-accent text-accent-foreground" : "text-muted-foreground"
                )
              }
            >
              <item.icon className="size-3.5" />
              {item.label}
            </NavLink>
          ))}
        </nav>

        <main className="flex-1 p-4 md:p-8">
          <div className="mx-auto w-full max-w-7xl">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  )
}
