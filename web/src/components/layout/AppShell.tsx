import { useEffect, useState, type ComponentType } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import {
  ArrowLeftRight,
  CreditCard,
  LayoutDashboard,
  LogOut,
  Menu,
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
  { to: "/settings/pillars", labelKey: "nav.settings", icon: Settings },
];

function NavLinks({ onNavigate }: { onNavigate?: () => void }) {
  const { t } = useTranslation();
  return (
    <nav className="flex flex-1 flex-col gap-1 p-3">
      {NAV_ITEMS.map(({ to, labelKey, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={to === "/"}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn(
              "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
              isActive
                ? "bg-primary text-primary-foreground"
                : "text-muted-foreground hover:bg-accent hover:text-accent-foreground",
            )
          }
        >
          <Icon className="size-4 shrink-0" />
          {t(labelKey)}
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
    <div className="flex min-h-screen flex-col md:flex-row">
      {/* Desktop sidebar */}
      <aside className="hidden w-64 shrink-0 flex-col border-r bg-card md:flex">
        <div className="flex h-14 items-center px-4 text-lg font-semibold">{t("app.name")}</div>
        <NavLinks />
      </aside>

      {/* Mobile drawer */}
      {mobileOpen && (
        <div className="fixed inset-0 z-40 md:hidden">
          <button
            type="button"
            aria-label={t("nav.closeMenu")}
            className="absolute inset-0 bg-black/50"
            onClick={() => setMobileOpen(false)}
          />
          <aside className="relative flex h-full w-64 flex-col bg-card shadow-lg">
            <div className="flex h-14 items-center justify-between px-4 text-lg font-semibold">
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
            <NavLinks onNavigate={() => setMobileOpen(false)} />
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 items-center justify-between gap-2 border-b bg-card px-4">
          <Button
            variant="ghost"
            size="icon"
            className="md:hidden"
            aria-label={t("nav.openMenu")}
            onClick={() => setMobileOpen(true)}
          >
            <Menu className="size-4" />
          </Button>
          <span className="text-sm font-medium md:hidden">{t("app.name")}</span>
          <div className="ml-auto flex items-center gap-2">
            <QuickTransactionButton />
            <ThemeToggle />
            <Button variant="outline" size="sm" onClick={handleLogout}>
              <LogOut className="size-4" />
              {t("auth.logout")}
            </Button>
          </div>
        </header>
        <main className="flex flex-1 flex-col">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
