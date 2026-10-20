import { useState } from "react";
import { Link, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import {
  CalendarDays,
  Calculator,
  Landmark,
  ChevronRight,
  CreditCard,
  Download,
  LogOut,
  Moon,
  Receipt,
  Repeat,
  Settings,
  Tags,
  Wallet,
} from "lucide-react";
import { toast } from "sonner";

import { useTheme } from "@/hooks/useTheme";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import { useGetCurrentUser, getGetCurrentUserQueryKey, useLogout } from "@/lib/api/generated/auth/auth";
import { exportTransactionsCsv } from "@/lib/api/generated/export/export";
import { translateApiError } from "@/lib/errors";

interface HubLink {
  to: string;
  labelKey: string;
  icon: typeof Wallet;
}

// Everything that lives in the desktop rail but not in the mobile tab bar
// (design_handoff_mk_finance/README.md §Mobile "Navegação" — mobile screen
// 12, "hub do que não cabe na tab bar").
const HUB_LINKS: HubLink[] = [
  { to: "/cards", labelKey: "nav.cards", icon: CreditCard },
  { to: "/cards", labelKey: "more.statements", icon: Receipt },
  { to: "/accounts", labelKey: "nav.accounts", icon: Wallet },
  { to: "/categories", labelKey: "nav.categories", icon: Tags },
  { to: "/recurrences", labelKey: "nav.recurrences", icon: Repeat },
  { to: "/calendar", labelKey: "nav.calendar", icon: CalendarDays },
  { to: "/investments", labelKey: "nav.investments", icon: Landmark },
  { to: "/simulator", labelKey: "nav.simulator", icon: Calculator },
  { to: "/settings/pillars", labelKey: "nav.settings", icon: Settings },
];

export default function MorePage() {
  const { t } = useTranslation();
  const { theme, toggleTheme } = useTheme();
  const workspaceId = useCurrentWorkspaceId();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const logoutMutation = useLogout();
  const { data: user } = useGetCurrentUser();
  const [exporting, setExporting] = useState(false);

  function handleLogout() {
    logoutMutation.mutate(undefined, {
      onSettled: () => {
        queryClient.removeQueries({ queryKey: getGetCurrentUserQueryKey() });
        navigate("/login", { replace: true });
      },
    });
  }

  async function handleExportCsv() {
    if (!workspaceId) return;
    setExporting(true);
    try {
      const csv = await exportTransactionsCsv(workspaceId);
      const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = "lancamentos.csv";
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      toast.error(translateApiError(t, error));
    } finally {
      setExporting(false);
    }
  }

  const initials = (user?.name ?? "").trim().slice(0, 2).toUpperCase();

  return (
    <div className="flex flex-col gap-3">
      <h1 className="pt-1 text-xl font-extrabold tracking-[-0.025em] text-ink-primary">
        {t("nav.more")}
      </h1>

      <div className="flex items-center gap-3 rounded-card bg-card p-4">
        <span className="flex size-[46px] shrink-0 items-center justify-center rounded-[15px] bg-success-bg text-[15px] font-extrabold text-success-ink">
          {initials}
        </span>
        <div className="flex min-w-0 flex-1 flex-col">
          <span className="truncate text-sm font-extrabold text-ink-primary">{user?.name}</span>
          <span className="truncate text-[11px] text-ink-tertiary">{user?.email}</span>
        </div>
      </div>

      <div className="flex flex-col rounded-card bg-card px-4">
        {HUB_LINKS.map(({ to, labelKey, icon: Icon }, index) => (
          <div key={labelKey}>
            {index > 0 && <div className="h-px bg-divider-row" />}
            <Link to={to} className="flex items-center gap-3 py-3.5">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-field bg-field text-ink-secondary">
                <Icon className="size-4" />
              </span>
              <span className="flex-1 text-[13px] font-bold text-ink-primary">{t(labelKey)}</span>
              <ChevronRight className="size-4 text-placeholder" />
            </Link>
          </div>
        ))}
      </div>

      <div className="flex flex-col rounded-card bg-card px-4">
        <div className="flex items-center gap-3 py-3.5">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-field bg-field text-ink-secondary">
            <Moon className="size-4" />
          </span>
          <span className="flex-1 text-[13px] font-bold text-ink-primary">{t("theme.dark")}</span>
          <button
            type="button"
            role="switch"
            aria-checked={theme === "dark"}
            aria-label={t("theme.toggle")}
            onClick={toggleTheme}
            className="relative h-6 w-11 shrink-0 rounded-pill bg-neutral transition-colors data-[on=true]:bg-mint"
            data-on={theme === "dark"}
          >
            <span
              className={`absolute top-0.5 size-5 rounded-pill bg-card shadow-[0_1px_3px_rgba(22,24,26,0.3)] transition-[left] ${theme === "dark" ? "left-[22px]" : "left-0.5"}`}
            />
          </button>
        </div>
        <div className="h-px bg-divider-row" />
        <button
          type="button"
          disabled={exporting}
          onClick={handleExportCsv}
          className="flex items-center gap-3 py-3.5 text-left disabled:opacity-60"
        >
          <span className="flex size-9 shrink-0 items-center justify-center rounded-field bg-field text-ink-secondary">
            <Download className="size-4" />
          </span>
          <span className="flex-1 text-[13px] font-bold text-ink-primary">{t("more.exportCsv")}</span>
          <ChevronRight className="size-4 text-placeholder" />
        </button>
        <div className="h-px bg-divider-row" />
        <button
          type="button"
          onClick={handleLogout}
          className="flex items-center gap-3 py-3.5 text-left"
        >
          <span className="flex size-9 shrink-0 items-center justify-center rounded-field bg-alert-bg text-alert-ink">
            <LogOut className="size-4" />
          </span>
          <span className="flex-1 text-[13px] font-bold text-alert-ink">{t("auth.logout")}</span>
        </button>
      </div>
    </div>
  );
}
