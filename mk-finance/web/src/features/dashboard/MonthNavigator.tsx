import { useTranslation } from "react-i18next";
import { ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import { formatMonth } from "@/lib/format";
import { shiftMonth } from "@/features/dashboard/dashboardData";

interface MonthNavigatorProps {
  month: string;
  onMonthChange: (month: string) => void;
}

export function MonthNavigator({ month, onMonthChange }: MonthNavigatorProps) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center gap-2">
      <Button
        variant="outline"
        size="icon-sm"
        aria-label={t("dashboard.month.previous")}
        onClick={() => onMonthChange(shiftMonth(month, -1))}
      >
        <ChevronLeft className="size-4" />
      </Button>
      <span className="min-w-40 text-center text-sm font-medium capitalize">
        {formatMonth(month)}
      </span>
      <Button
        variant="outline"
        size="icon-sm"
        aria-label={t("dashboard.month.next")}
        onClick={() => onMonthChange(shiftMonth(month, 1))}
      >
        <ChevronRight className="size-4" />
      </Button>
    </div>
  );
}
