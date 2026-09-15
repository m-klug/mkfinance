import { useEffect, useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Info } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { TextField } from "@/components/form/fields";
import { cn } from "@/lib/utils";
import { formatDate } from "@/lib/format";
import { translateApiError } from "@/lib/errors";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import {
  getListPillarAllocationsQueryKey,
  useListPillarAllocations,
  useUpdatePillarAllocations,
} from "@/lib/api/generated/pillars/pillars";
import type { Pillar } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { PILLAR_COLORS } from "@/features/dashboard/dashboardFormat";
import {
  PILLAR_ORDER,
  earliestEffectiveFrom,
  sumPercents,
  toAllocationsRequest,
  toPillarPercentForm,
  type PillarPercentForm,
} from "@/features/settings/pillarAllocationForm";

const TARGET_SUM = 100;

export default function PillarSettingsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const workspaceId = useCurrentWorkspaceId();

  const { data: allocations, isPending } = useListPillarAllocations(
    workspaceId ?? "",
    undefined,
    { query: { enabled: Boolean(workspaceId) } },
  );

  const [form, setForm] = useState<PillarPercentForm | undefined>(undefined);
  // Only hydrate the form once — a background refetch (e.g. window refocus)
  // must never clobber an in-progress edit.
  const hydratedRef = useRef(false);

  useEffect(() => {
    if (allocations && !hydratedRef.current) {
      setForm(toPillarPercentForm(allocations));
      hydratedRef.current = true;
    }
  }, [allocations]);

  const updateMutation = useUpdatePillarAllocations();

  const sum = form ? sumPercents(form) : 0;
  const isValidSum = sum === TARGET_SUM;
  const effectiveFrom = allocations ? earliestEffectiveFrom(allocations) : undefined;

  function handlePercentChange(pillar: Pillar, rawValue: string) {
    if (!form) return;
    const parsed = Number(rawValue);
    const clamped = rawValue === "" || Number.isNaN(parsed) ? 0 : Math.max(0, Math.min(100, Math.trunc(parsed)));
    setForm({ ...form, [pillar]: clamped });
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!workspaceId || !form || !isValidSum) return;

    updateMutation.mutate(
      { workspaceId, data: { allocations: toAllocationsRequest(form) } },
      {
        onSuccess: (updated) => {
          void queryClient.invalidateQueries({
            queryKey: getListPillarAllocationsQueryKey(workspaceId),
          });
          setForm(toPillarPercentForm(updated));
          toast.success(t("settings.pillars.updated"));
        },
        onError: (error) => {
          toast.error(translateApiError(t, error));
        },
      },
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-6 p-4 md:p-8">
      <div>
        <p className="text-[11px] font-bold uppercase tracking-[0.14em] text-ink-label">
          {t("nav.settings")}
        </p>
        <h1 className="text-[26px] font-extrabold tracking-[-0.025em] text-ink-primary">
          {t("settings.pillars.title")}
        </h1>
        <p className="text-sm text-ink-secondary">{t("settings.pillars.subtitle")}</p>
      </div>

      {(isPending || !form) && <p className="text-ink-tertiary">{t("common.loading")}</p>}

      {form && (
        <Card className="gap-4 rounded-card border border-divider-card bg-card p-5">
          <CardHeader>
            <CardTitle className="text-[15px] font-extrabold tracking-[-0.015em] text-ink-primary">
              {t("settings.pillars.formTitle")}
            </CardTitle>
            <CardDescription className="text-ink-secondary">
              {effectiveFrom
                ? t("settings.pillars.inForceSince", { date: formatDate(effectiveFrom) })
                : undefined}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form className="flex flex-col gap-5" onSubmit={handleSubmit} noValidate>
              <div className="flex flex-col gap-3">
                {PILLAR_ORDER.map((pillar) => (
                  <div
                    key={pillar}
                    className="flex flex-col gap-2 rounded-list-item bg-field p-3.5 sm:flex-row sm:items-center sm:gap-4"
                  >
                    <div className="flex items-center gap-2.5 sm:w-44 sm:shrink-0">
                      <span
                        className="size-2.5 shrink-0 rounded-[4px]"
                        style={{ backgroundColor: PILLAR_COLORS[pillar] }}
                        aria-hidden
                      />
                      <span className="text-sm font-bold text-ink-primary">
                        {t(`pillar.${pillar}`)}
                      </span>
                    </div>
                    <input
                      type="range"
                      min={0}
                      max={100}
                      step={1}
                      value={form[pillar]}
                      onChange={(event) => handlePercentChange(pillar, event.target.value)}
                      aria-hidden
                      tabIndex={-1}
                      className="h-[9px] flex-1 cursor-pointer appearance-none rounded-pill bg-neutral accent-[color:var(--mint)]"
                    />
                    <div className="flex items-center gap-1 sm:w-20 sm:justify-end">
                      <TextField
                        id={`pillar-target-${pillar}`}
                        label={t(`pillar.${pillar}`)}
                        fieldClassName="[&>label]:sr-only"
                        className="w-16 text-right tabular-nums"
                        type="number"
                        inputMode="numeric"
                        min={0}
                        max={100}
                        step={1}
                        // Empty (rather than "0") while zero, mirroring MoneyField —
                        // otherwise clearing the field to retype leaves a stray "0"
                        // that the next keystroke lands next to unpredictably.
                        value={form[pillar] === 0 ? "" : String(form[pillar])}
                        onChange={(event) => handlePercentChange(pillar, event.target.value)}
                      />
                      <span className="text-sm font-bold text-ink-secondary">%</span>
                    </div>
                  </div>
                ))}
              </div>

              <div
                className={cn(
                  "flex items-center justify-between gap-3 rounded-list-item p-3.5 text-sm font-bold",
                  isValidSum ? "bg-success-bg text-success-ink" : "bg-alert-bg text-alert-ink",
                )}
              >
                <span className="tabular-nums">{t("settings.pillars.sumLabel", { sum })}</span>
                {isValidSum ? (
                  <Badge variant="success">{t("settings.pillars.sumOk")}</Badge>
                ) : (
                  <span>{t("settings.pillars.sumError")}</span>
                )}
              </div>

              <div className="flex items-start gap-2.5 rounded-card bg-hero p-4 text-hero-foreground">
                <Info className="mt-0.5 size-4 shrink-0 text-hero-foreground/70" aria-hidden />
                <p role="status" className="text-sm text-hero-foreground/85">
                  {t("settings.pillars.effectiveFromWarning")}
                </p>
              </div>

              <div>
                <Button type="submit" disabled={!isValidSum || updateMutation.isPending}>
                  {updateMutation.isPending ? t("common.saving") : t("common.save")}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
