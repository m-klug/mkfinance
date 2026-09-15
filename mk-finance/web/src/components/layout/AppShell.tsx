import { useEffect, useState, type ComponentType } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeftRight,
  CalendarDays,
  CreditCard,
  LayoutDashboard,
  LogOut,
  Menu,
  Repeat,
  Settings,
  Tags,
  Wallet,
  X,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { ThemeToggle } from "@/components/theme/ThemeToggle";
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
  { to: "/settings/pillars", labelKey: "nav.settings", icon: Settings },
];

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
  const [mobileOpen, setMobileOpen] = useState(false);
  const logoutMutation = useLogout();

  // Close the mobile drawer whenever the route changes (e.g. after tapping
  // a nav link), so it never stays open across navigations.
  useEffect(() => {
    setMobileOpen(false);
  }, [location.pathname]);

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
    <div className="flex min-h-screen bg-page-bg p-3.5">
      <div className="flex min-h-[calc(100vh-1.75rem)] w-full overflow-hidden rounded-shell bg-shell-bg shadow-[var(--shadow-shell)] md:flex-row">
        {/* Desktop rail */}
        <aside className="hidden w-[72px] shrink-0 flex-col items-center border-r border-divider-card bg-card py-4 md:flex">
          <div className="mb-4 flex size-9 items-center justify-center rounded-[10px] bg-mint text-sm font-extrabold text-mint-ink">
            M
          </div>
          <NavRail className="flex-1" />
          <div className="flex flex-col items-center gap-2 pt-2">
            <ThemeToggle />
          </div>
        </aside>

        {/* Mobile drawer — scrim covers the whole shell per the handoff's modal rule */}
        {mobileOpen && (
          <div className="fixed inset-3.5 z-40 overflow-hidden rounded-shell md:hidden">
            <button
              type="button"
              aria-label={t("nav.closeMenu")}
              className="absolute inset-0 bg-[var(--scrim)]"
              onClick={() => setMobileOpen(false)}
            />
            <aside className="relative flex h-full w-64 flex-col bg-card">
              <div className="flex h-14 items-center justify-between px-4 text-lg font-extrabold">
                {t("app.name")}
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={t("nav.closeMenu")}
                  onClick={() => setMobileOpen(false)}
                >
                  <X className="size-4" />
                </Button>
              </div>
              <NavRail onNavigate={() => setMobileOpen(false)} className="flex-1 items-stretch px-3" />
            </aside>
          </div>
        )}

        <div className="flex min-w-0 flex-1 flex-col">
          <header className="flex h-16 items-center justify-between gap-2 border-b border-divider-card px-4 md:px-6">
            <Button
              variant="ghost"
              size="icon"
              className="md:hidden"
              aria-label={t("nav.openMenu")}
              onClick={() => setMobileOpen(true)}
            >
              <Menu className="size-4" />
            </Button>
            <span className="text-sm font-bold md:hidden">{t("app.name")}</span>
            <div className="ml-auto flex items-center gap-2">
              <QuickTransactionButton />
              <span className="hidden md:contents">
                <ThemeToggle />
                <Button variant="secondary" size="sm" onClick={handleLogout}>
                  <LogOut className="size-4" />
                  {t("auth.logout")}
                </Button>
              </span>
            </div>
          </header>
          <main className="flex flex-1 flex-col overflow-y-auto p-4 md:p-6">
            <Outlet />
          </main>
        </div>
      </div>
    </div>
  );
}
