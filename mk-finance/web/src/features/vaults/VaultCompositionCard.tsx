import { useTranslation } from "react-i18next";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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
 */
export function VaultCompositionCard({ composition }: VaultCompositionCardProps) {
  const { t } = useTranslation();
  const segments = vaultBarSegments(composition, t("vaults.unallocated"));
  const overdrawn = isUnallocatedOverdrawn(composition);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{t("vaults.compositionTitle")}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="flex h-4 w-full overflow-hidden rounded-full bg-muted">
          {segments.map((segment) => (
            <div
              key={segment.key}
              className="h-full first:rounded-l-full last:rounded-r-full"
              style={{ width: `${segment.percent}%`, backgroundColor: segment.color }}
              title={`${segment.label}: ${formatCents(segment.cents)}`}
            />
          ))}
        </div>

        <ul className="flex flex-wrap gap-x-6 gap-y-2 text-sm">
          {segments.map((segment) => (
            <li key={segment.key} className="flex items-center gap-2">
              <span
                className="size-2.5 rounded-full"
                style={{ backgroundColor: segment.color }}
                aria-hidden
              />
              <span>{segment.label}</span>
              <span className="tabular-nums text-muted-foreground">
                {formatCents(segment.cents)}
              </span>
            </li>
          ))}
        </ul>

        {overdrawn && (
          <p className="rounded-md bg-destructive/15 px-3 py-2 text-sm font-medium text-destructive">
            {t("vaults.unallocated")}:{" "}
            {t("vaults.overdrawn", { amount: formatCents(Math.abs(composition.unallocatedCents)) })}
          </p>
        )}
      </CardContent>
    </Card>
  );
}
