import { useTranslation } from "react-i18next";

import { formatCents } from "@/lib/format";
import type { VaultComposition } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { isUnallocatedOverdrawn, vaultBarSegments } from "@/features/vaults/vaultOptions";

interface VaultCompositionCardProps {
  composition: VaultComposition;
}

/**
 * The stacked bar the whole feature revolves around: one segment per vault
 * plus the unallocated slice, all numbers taken straight from the server
 * (CT-2 decision 1 — this component never sums money on its own). A negative
 * unallocated slice is not squeezed into the bar (§4.3) — it gets its own
 * red callout with the amount that is uncovered instead.
 *
 * Styled as the dark hero panel from the handoff (screen 06 — Caixinhas):
 * account balance + allocated/unallocated at the top, the segmented
 * composition bar underneath, legend at the bottom.
 */
export function VaultCompositionCard({ composition }: VaultCompositionCardProps) {
  const { t } = useTranslation();
  const segments = vaultBarSegments(composition, t("vaults.unallocated"));
  const overdrawn = isUnallocatedOverdrawn(composition);

  return (
    <div className="flex flex-col gap-4 rounded-card bg-hero p-5 text-hero-foreground">
      <div className="flex flex-wrap items-end gap-4">
        <div className="flex flex-col gap-0.5">
          <span className="text-[11px] text-[#8d9298]">{t("vaults.balance")}</span>
          <span className="tabular-nums text-[28px] font-extrabold tracking-tight text-hero-foreground">
            {formatCents(composition.balanceCents)}
          </span>
        </div>
        <div className="ml-auto flex gap-5">
          <div className="flex flex-col">
            <span className="text-[11px] text-[#8d9298]">{t("vaults.allocated")}</span>
            <span className="tabular-nums text-[15px] font-bold text-hero-foreground">
              {formatCents(composition.allocatedCents)}
            </span>
          </div>
          <div className="flex flex-col">
            <span className="text-[11px] text-[#8d9298]">{t("vaults.unallocated")}</span>
            <span
              className={`tabular-nums text-[15px] font-bold ${overdrawn ? "text-[#ffb4a2]" : "text-mint"}`}
            >
              {formatCents(composition.unallocatedCents)}
            </span>
          </div>
        </div>
      </div>

      {segments.length > 0 && (
        <div className="flex h-[26px] gap-[3px]">
          {segments.map((segment) => (
            <div
              key={segment.key}
              className="h-full rounded-[6px]"
              style={{ width: `${segment.percent}%`, backgroundColor: segment.color }}
              title={`${segment.label}: ${formatCents(segment.cents)}`}
            />
          ))}
        </div>
      )}

      <ul className="flex flex-wrap items-center gap-x-4.5 gap-y-2 text-[11px]">
        {segments.map((segment) => (
          <li key={segment.key} className="flex items-center gap-1.5 text-[#c9cdd1]">
            <span
              className="size-2.5 rounded-[3px]"
              style={{ backgroundColor: segment.color }}
              aria-hidden
            />
            <span>{segment.label}</span>
            <span className="tabular-nums">{formatCents(segment.cents)}</span>
          </li>
        ))}
      </ul>

      {overdrawn && (
        <p className="rounded-field bg-alert-bg px-3 py-2 text-sm font-medium text-alert-ink">
          {t("vaults.unallocated")}:{" "}
          {t("vaults.overdrawn", { amount: formatCents(Math.abs(composition.unallocatedCents)) })}
        </p>
      )}
    </div>
  );
}
