import { useTranslation } from "react-i18next";

import { cn } from "@/lib/utils";
import { formatCents } from "@/lib/format";
import { VaultGoalStatus, type VaultGoal } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { clampPercent, goalMonthlyLabel, goalStatusBadgeClass } from "@/features/vaults/vaultOptions";

interface VaultGoalBarProps {
  goal: VaultGoal;
  /** The vault's own settled balance — shown next to the target, never used for maths (§4.6: the API computes progress/remaining/monthlyNeeded, this component only formats them). */
  currentCents: number;
}

const FILL_CLASS: Record<VaultGoalStatus, string> = {
  [VaultGoalStatus.IN_PROGRESS]: "bg-blue-500",
  [VaultGoalStatus.REACHED]: "bg-emerald-500",
  [VaultGoalStatus.OVERDUE]: "bg-destructive",
};

export function VaultGoalBar({ goal, currentCents }: VaultGoalBarProps) {
  const { t } = useTranslation();
  const width = clampPercent(goal.progressPercent);

  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center justify-between gap-2 text-xs">
        <span
          className={cn("rounded px-1.5 py-0.5 font-medium", goalStatusBadgeClass(goal.status))}
        >
          {t(`vaults.goalStatus.${goal.status}`)}
        </span>
        <span className="text-muted-foreground">
          {t("vaults.goal.progress", { percent: Math.round(goal.progressPercent) })}
        </span>
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
        <div
          className={cn("h-full rounded-full", FILL_CLASS[goal.status])}
          style={{ width: `${width}%` }}
        />
      </div>
      <div className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
        <span>
          {formatCents(currentCents)} / {t("vaults.goal.target", { amount: formatCents(goal.targetAmountCents) })}
        </span>
        <span>{goalMonthlyLabel(t, goal)}</span>
      </div>
    </div>
  );
}
