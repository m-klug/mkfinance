import { useEffect, useRef, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Info } from "lucide-react";

import { Button } from "@/components/ui/button";
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
        <h1 className="text-2xl font-semibold">{t("settings.pillars.title")}</h1>
        <p className="text-sm text-muted-foreground">{t("settings.pillars.subtitle")}</p>
      </div>

      {(isPending || !form) && <p className="text-muted-foreground">{t("common.loading")}</p>}

      {form && (
        <Card>
          <CardHeader>
            <CardTitle>{t("settings.pillars.formTitle")}</CardTitle>
            <CardDescription>
              {effectiveFrom
                ? t("settings.pillars.inForceSince", { date: formatDate(effectiveFrom) })
                : undefined}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
              <p
                role="status"
                className="flex items-start gap-2 rounded-md border border-amber-500/40 bg-amber-500/10 p-3 text-sm text-amber-700 dark:text-amber-400"
              >
                <Info className="mt-0.5 size-4 shrink-0" aria-hidden />
                {t("settings.pillars.effectiveFromWarning")}
              </p>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                {PILLAR_ORDER.map((pillar) => (
                  <TextField
                    key={pillar}
                    id={`pillar-target-${pillar}`}
                    label={t(`pillar.${pillar}`)}
                    type="number"
                    inputMode="numeric"
                    min={0}
                    max={100}
                    step={1}
                    value={String(form[pillar])}
                    onChange={(event) => handlePercentChange(pillar, event.target.value)}
                  />
                ))}
              </div>

              <div
                className={cn(
                  "flex items-center justify-between rounded-md border p-3 text-sm font-medium",
                  isValidSum
                    ? "border-primary/40 bg-primary/5 text-primary"
                    : "border-destructive/40 bg-destructive/10 text-destructive",
                )}
              >
                <span>{t("settings.pillars.sumLabel", { sum })}</span>
                {!isValidSum && <span>{t("settings.pillars.sumError")}</span>}
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
