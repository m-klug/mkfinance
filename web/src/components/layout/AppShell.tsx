import { type ComponentType } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeftRight,
  CalendarDays,
  Calculator,
  Landmark,
  CreditCard,
  Ellipsis,
  LayoutDashboard,
  LogOut,
  PiggyBank,
  Repeat,
  Settings,
  Tags,
  Wallet,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
import { ValuesVisibilityToggle } from "@/components/privacy/ValuesVisibilityToggle";
import { cn } from "@/lib/utils";
import { getGetCurrentUserQueryKey, useLogout } from "@/lib/api/generated/auth/auth";
// Quick entry has to be one tap away from every screen (PLANEJAMENTO §6.3),
// so the shell reserves this single slot for the FE-2 component.
import { QuickTransactionButton } from "@/features/transactions/QuickTransactionButton";

interface NavItem {
  to: string;
  labelKey: string;
  icon: ComponentType<{ className?: string }>;
}

const NAV_ITEMS: NavItem[] = [
  { to: "/", labelKey: "nav.dashboard", icon: LayoutDashboard },
  { to: "/transactions", labelKey: "nav.transactions", icon: ArrowLeftRight },
  { to: "/accounts", labelKey: "nav.accounts", icon: Wallet },
  { to: "/cards", labelKey: "nav.cards", icon: CreditCard },
  { to: "/categories", labelKey: "nav.categories", icon: Tags },
  { to: "/recurrences", labelKey: "nav.recurrences", icon: Repeat },
  { to: "/calendar", labelKey: "nav.calendar", icon: CalendarDays },
  { to: "/investments", labelKey: "nav.investments", icon: Landmark },
  { to: "/simulator", labelKey: "nav.simulator", icon: Calculator },
  { to: "/settings/pillars", labelKey: "nav.settings", icon: Settings },
];

// Everything that lives inside the mobile "Mais" hub (design_handoff_mk_finance/README.md
// §Mobile) — the tab bar's "Mais" item stays highlighted while the user is
// anywhere under one of these, not just on /more itself.
const MORE_HUB_PREFIXES = ["/more", "/cards", "/accounts", "/categories", "/recurrences", "/calendar", "/investments", "/simulator", "/settings"];

/**
 * Icon-only rail, 9 destinations + theme/logout footer
 * (design_handoff_mk_finance/README.md §Componentes recorrentes).
 */
function NavRail({ onNavigate, className }: { onNavigate?: () => void; className?: string }) {
  const { t } = useTranslation();
  return (
    <nav className={cn("flex flex-col items-center gap-1 py-3", className)}>
      {NAV_ITEMS.map(({ to, labelKey, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={to === "/"}
          onClick={onNavigate}
          title={t(labelKey)}
          aria-label={t(labelKey)}
          className={({ isActive }) =>
            cn(
              "flex size-10 shrink-0 items-center justify-center rounded-[13px] transition-colors md:size-11",
              isActive
                ? "bg-success-bg text-success-ink"
                : "text-ink-tertiary hover:bg-neutral hover:text-ink-secondary",
            )
          }
        >
          <Icon className="size-[18px] shrink-0" />
        </NavLink>
      ))}
    </nav>
  );
}

export function AppShell() {
  const { t } = useTranslation();
  const location = useLocation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const logoutMutation = useLogout();
  const isMoreActive = MORE_HUB_PREFIXES.some((prefix) => location.pathname.startsWith(prefix));

  function handleLogout() {
    logoutMutation.mutate(undefined, {
      onSettled: () => {
        // setQueryData(key, undefined) is a no-op in TanStack Query v5 (it
        // only skips the update), so evict the cache entry instead —
        // otherwise RequireAuth would keep treating the stale cached user
        // as authenticated if the app ever revisits a protected route.
        queryClient.removeQueries({ queryKey: getGetCurrentUserQueryKey() });
        navigate("/login", { replace: true });
      },
    });
  }

  return (
    <div className="flex min-h-screen bg-page-bg p-3.5 max-sm:p-0">
      <div className="flex min-h-[calc(100vh-1.75rem)] w-full overflow-hidden rounded-shell bg-shell-bg shadow-[var(--shadow-shell)] max-sm:min-h-screen max-sm:rounded-none max-sm:shadow-none md:flex-row">
        {/* Desktop rail */}
        <aside className="hidden w-[72px] shrink-0 flex-col items-center border-r border-divider-card bg-card py-4 md:flex">
          <div className="mb-4 flex size-9 items-center justify-center rounded-[10px] bg-mint text-sm font-extrabold text-mint-ink">
            M
          </div>
          <NavRail className="flex-1" />
          <div className="flex flex-col items-center gap-2 pt-2">
            <ValuesVisibilityToggle />
            <ThemeToggle />
          </div>
        </aside>

        <div className="flex min-w-0 flex-1 flex-col">
          {/* Desktop topbar — the mobile equivalents (quick entry FAB, values/theme
              toggles, sign out) live in the bottom tab bar and the Mais hub instead. */}
          <header className="hidden h-16 items-center justify-between gap-2 border-b border-divider-card px-4 md:flex md:px-6">
            <span className="text-sm font-bold">{t("app.name")}</span>
            <div className="ml-auto flex items-center gap-2">
              <QuickTransactionButton />
              <ValuesVisibilityToggle />
              <ThemeToggle />
              <Button variant="secondary" size="sm" onClick={handleLogout}>
                <LogOut className="size-4" />
                {t("auth.logout")}
              </Button>
            </div>
          </header>
          <main className="flex flex-1 flex-col overflow-y-auto p-4 pb-[calc(64px+env(safe-area-inset-bottom)+16px)] md:p-6">
            <Outlet />
          </main>

          {/* Mobile tab bar — Início · Extrato · FAB · Caixas · Mais
              (design_handoff_mk_finance/README.md §Mobile "Navegação"). */}
          <nav
            className="fixed inset-x-0 bottom-0 z-30 grid h-[calc(64px+env(safe-area-inset-bottom))] grid-cols-5 items-center bg-card px-4 pb-[env(safe-area-inset-bottom)] shadow-[0_-4px_16px_rgba(22,24,26,0.06)] md:hidden"
            aria-label={t("app.name")}
          >
            <NavLink
              to="/"
              end
              aria-label={t("nav.home")}
              className={({ isActive }) =>
                cn(
                  "flex flex-col items-center gap-0.5 justify-self-center",
                  isActive ? "font-extrabold text-success-ink" : "text-ink-tertiary",
                )
              }
            >
              <LayoutDashboard className="size-[18px]" />
              <span className="text-[10px] font-bold">{t("nav.home")}</span>
            </NavLink>
            <NavLink
              to="/transactions"
              aria-label={t("nav.statement")}
              className={({ isActive }) =>
                cn(
                  "flex flex-col items-center gap-0.5 justify-self-center",
                  isActive ? "font-extrabold text-success-ink" : "text-ink-tertiary",
                )
              }
            >
              <ArrowLeftRight className="size-[18px]" />
              <span className="text-[10px] font-bold">{t("nav.statement")}</span>
            </NavLink>
            <div className="justify-self-center">
              <QuickTransactionButton variant="fab" />
            </div>
            <NavLink
              to="/vaults"
              aria-label={t("nav.vaults")}
              className={({ isActive }) =>
                cn(
                  "flex flex-col items-center gap-0.5 justify-self-center",
                  isActive ? "font-extrabold text-success-ink" : "text-ink-tertiary",
                )
              }
            >
              <PiggyBank className="size-[18px]" />
              <span className="text-[10px] font-bold">{t("nav.vaults")}</span>
            </NavLink>
            <NavLink
              to="/more"
              aria-label={t("nav.more")}
              className={cn(
                "flex flex-col items-center gap-0.5 justify-self-center",
                isMoreActive ? "font-extrabold text-success-ink" : "text-ink-tertiary",
              )}
            >
              <Ellipsis className="size-[18px]" />
              <span className="text-[10px] font-bold">{t("nav.more")}</span>
            </NavLink>
          </nav>
        </div>
      </div>
    </div>
  );
}
