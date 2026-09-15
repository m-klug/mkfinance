import { cn } from "@/lib/utils";

interface TargetBarProps {
  /** Actual value as % of income (0–100+, can exceed the axis). */
  actualPercent: number;
  /** Target % marker position. */
  targetPercent: number;
  /** Fill color — a pillar color from dashboardFormat.PILLAR_COLORS. */
  color: string;
  /** Axis scale per the handoff: 0–50% of income maps to the full track width. */
  axisMax?: number;
  className?: string;
}

/**
 * The app's signature element (design_handoff_mk_finance/README.md):
 * a track with a colored fill for the actual share plus a vertical marker
 * for the target. Used in the dashboard pillar panel, the quick-entry pillar
 * impact preview, and the pillar target settings screen.
 */
export function TargetBar({ actualPercent, targetPercent, color, axisMax = 50, className }: TargetBarProps) {
  const toWidth = (value: number) => `${Math.min(100, Math.max(0, (value / axisMax) * 100))}%`;

  return (
    <div
      className={cn("relative h-[9px] w-full overflow-hidden rounded-pill bg-neutral dark:bg-[#2B2F34]", className)}
      role="img"
      aria-label={`${actualPercent.toFixed(1)}% de ${targetPercent}% alvo`}
    >
      <div
        className="h-full rounded-pill transition-[width]"
        style={{ width: toWidth(actualPercent), backgroundColor: color }}
      />
      <div
        className="absolute top-0 h-full w-[2px] bg-ink-primary"
        style={{ left: toWidth(targetPercent) }}
      />
    </div>
  );
}
