import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { TextField } from "@/components/form/fields";
import { cn } from "@/lib/utils";
import { RecurrenceScope } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { todayIso } from "@/features/recurrences/recurrenceOptions";

interface RecurrenceScopeDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Edit and delete share the same scope choice, only the copy differs. */
  variant: "edit" | "delete";
  pending?: boolean;
  onConfirm: (scope: RecurrenceScope, occurrenceDate?: string) => void;
}

// A rule that may already have materialized occurrences (recurrenceOptions.
// mayHaveMaterialized) needs the user to say how far an edit/delete reaches —
// this is the calendar-app-style "this event or the whole series?" prompt.
export function RecurrenceScopeDialog({
  open,
  onOpenChange,
  variant,
  pending,
  onConfirm,
}: RecurrenceScopeDialogProps) {
  const { t } = useTranslation();
  const [scope, setScope] = useState<RecurrenceScope>(RecurrenceScope.THIS_AND_FUTURE);
  const [occurrenceDate, setOccurrenceDate] = useState(todayIso());

  useEffect(() => {
    if (open) {
      setScope(RecurrenceScope.THIS_AND_FUTURE);
      setOccurrenceDate(todayIso());
    }
  }, [open]);

  const isThisOne = scope === RecurrenceScope.THIS_ONE;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t(`recurrences.scope.${variant}Title`)}</DialogTitle>
          <DialogDescription>{t(`recurrences.scope.${variant}Description`)}</DialogDescription>
        </DialogHeader>
        <div className="flex flex-col gap-2.5">
          <label
            className={cn(
              "flex cursor-pointer items-start gap-3 rounded-list-item border border-divider-card bg-field p-3.5 text-sm transition-colors",
              isThisOne && "border-transparent bg-success-bg",
            )}
          >
            <input
              type="radio"
              name="recurrence-scope"
              className="mt-1 size-4 accent-[color:var(--mint)]"
              checked={isThisOne}
              onChange={() => setScope(RecurrenceScope.THIS_ONE)}
            />
            <span className="flex flex-col gap-0.5">
              <span className="font-bold text-ink-primary">{t("recurrences.scope.thisOne")}</span>
              <span className="text-ink-secondary">
                {t(`recurrences.scope.thisOne${variant === "delete" ? "DeleteHint" : "EditHint"}`)}
              </span>
            </span>
          </label>
          <label
            className={cn(
              "flex cursor-pointer items-start gap-3 rounded-list-item border border-divider-card bg-field p-3.5 text-sm transition-colors",
              !isThisOne && "border-transparent bg-success-bg",
            )}
          >
            <input
              type="radio"
              name="recurrence-scope"
              className="mt-1 size-4 accent-[color:var(--mint)]"
              checked={!isThisOne}
              onChange={() => setScope(RecurrenceScope.THIS_AND_FUTURE)}
            />
            <span className="flex flex-col gap-0.5">
              <span className="font-bold text-ink-primary">{t("recurrences.scope.thisAndFuture")}</span>
              <span className="text-ink-secondary">
                {t(
                  `recurrences.scope.thisAndFuture${variant === "delete" ? "DeleteHint" : "EditHint"}`,
                )}
              </span>
            </span>
          </label>

          {isThisOne && (
            <TextField
              id="recurrence-scope-occurrence-date"
              label={t("recurrences.scope.occurrenceDate")}
              hint={t("recurrences.scope.occurrenceDateHint")}
              type="date"
              value={occurrenceDate}
              onChange={(event) => setOccurrenceDate(event.target.value)}
            />
          )}
        </div>
        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
            {t("common.cancel")}
          </Button>
          <Button
            type="button"
            variant={variant === "delete" ? "destructive" : "default"}
            disabled={pending || (isThisOne && !occurrenceDate)}
            onClick={() => onConfirm(scope, isThisOne ? occurrenceDate : undefined)}
          >
            {t(`recurrences.scope.${variant}Confirm`)}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
