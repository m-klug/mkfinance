import { useTranslation } from "react-i18next";

import { cn } from "@/lib/utils";
import { useFormatCents } from "@/hooks/useFormatCents";
import { VaultGoalStatus, type VaultGoal } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { clampPercent, goalMonthlyLabel, goalStatusBadgeClass } from "@/features/vaults/vaultOptions";

interface VaultGoalBarProps {
  goal: VaultGoal;
  /** The vault's own settled balance — shown next to the target, never used for maths (§4.6: the API computes progress/remaining/monthlyNeeded, this component only formats them). */
  currentCents: number;
}

const FILL_CLASS: Record<VaultGoalStatus, string> = {
  [VaultGoalStatus.IN_PROGRESS]: "bg-mint",
  [VaultGoalStatus.REACHED]: "bg-success-ink",
  [VaultGoalStatus.OVERDUE]: "bg-alert-ink",
};

export function VaultGoalBar({ goal, currentCents }: VaultGoalBarProps) {
  const { t } = useTranslation();
  const formatMoney = useFormatCents();
  const width = clampPercent(goal.progressPercent);

  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-baseline gap-2">
        <span className="text-[11px] font-bold text-ink-label">
          {t("vaults.goal.target", { amount: formatMoney(goal.targetAmountCents) })}
        </span>
        <span className="ml-auto text-[11px] font-extrabold tabular-nums text-ink-primary">
          {t("vaults.goal.progress", { percent: Math.round(goal.progressPercent) })}
        </span>
      </div>
      <div className="relative h-2 w-full overflow-hidden rounded-pill bg-neutral">
        <div
          className={cn("h-full rounded-pill transition-[width]", FILL_CLASS[goal.status])}
          style={{ width: `${width}%` }}
        />
      </div>
      <div className="flex items-center justify-between gap-2 text-[10px] text-ink-label">
        <span
          className={cn(
            "rounded-pill px-1.5 py-0.5 font-bold uppercase tracking-[0.04em]",
            goalStatusBadgeClass(goal.status),
          )}
        >
          {t(`vaults.goalStatus.${goal.status}`)}
        </span>
        <span className="tabular-nums">{goalMonthlyLabel(t, goal, formatMoney)}</span>
      </div>
      <span className="tabular-nums text-[10px] text-ink-label">{formatMoney(currentCents)}</span>
    </div>
  );
}
