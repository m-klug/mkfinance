import { useTranslation } from "react-i18next";
import { ChevronLeft, ChevronRight } from "lucide-react";

import { formatMonth } from "@/lib/format";
import { shiftMonth } from "@/features/dashboard/dashboardData";

interface MonthNavigatorProps {
  month: string;
  onMonthChange: (month: string) => void;
}

/** Pill month selector (design_handoff_mk_finance/README.md §Screen 01 topbar). */
export function MonthNavigator({ month, onMonthChange }: MonthNavigatorProps) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center gap-0.5 rounded-pill bg-card p-1">
      <button
        type="button"
        aria-label={t("dashboard.month.previous")}
        onClick={() => onMonthChange(shiftMonth(month, -1))}
        className="flex size-8 items-center justify-center rounded-pill text-ink-tertiary transition-colors hover:bg-neutral hover:text-ink-primary"
      >
        <ChevronLeft className="size-4" />
      </button>
      <span className="min-w-32 px-2 text-center text-xs font-bold tabular-nums text-ink-primary capitalize">
        {formatMonth(month)}
      </span>
      <button
        type="button"
        aria-label={t("dashboard.month.next")}
        onClick={() => onMonthChange(shiftMonth(month, 1))}
        className="flex size-8 items-center justify-center rounded-pill text-ink-tertiary transition-colors hover:bg-neutral hover:text-ink-primary"
      >
        <ChevronRight className="size-4" />
      </button>
    </div>
  );
}
