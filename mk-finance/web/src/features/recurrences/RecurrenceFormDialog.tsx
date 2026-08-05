import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { MoneyField, SelectField, TextField } from "@/components/form/fields";
import { ApiError } from "@/lib/api/fetcher";
import { translateApiError } from "@/lib/errors";
import { formatDate } from "@/lib/format";
import {
  getListRecurrencesQueryKey,
  useCreateRecurrence,
  useUpdateRecurrence,
} from "@/lib/api/generated/recurrences/recurrences";
import {
  DayOfWeek,
  RecurrenceFrequency,
  RecurrenceRequestType,
  RecurrenceScope,
  type Account,
  type Category,
  type Recurrence,
  type RecurrenceRequest,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import {
  accountOptions,
  categoryOptionsForType,
  dayOfWeekOptions,
  frequencyOptions,
  recurrenceTypeOptions,
  todayIso,
  translateFieldError,
} from "@/features/recurrences/recurrenceOptions";

interface RecurrenceFormDialogProps {
  workspaceId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Present when editing; absent when creating. */
  recurrence?: Recurrence;
  accounts: Account[];
  categories: Category[];
  /**
   * Present when the edit was routed through RecurrenceScopeDialog. THIS_ONE
   * touches only the materialized occurrence, so the server ignores every
   * scheduling field — the form hides that section rather than show inputs
   * that would silently do nothing.
   */
  scope?: RecurrenceScope;
  occurrenceDate?: string;
}

interface FormState {
  type: RecurrenceRequestType;
  accountId: string;
  categoryId: string;
  amountCents: number;
  description: string;
  note: string;
  frequency: RecurrenceFrequency;
  dayOfMonth: string;
  dayOfWeek: string;
  monthOfYear: string;
  startDate: string;
  endDate: string;
  active: boolean;
}

interface FormErrors {
  accountId?: string;
  categoryId?: string;
  amountCents?: string;
  description?: string;
  dayOfMonth?: string;
  dayOfWeek?: string;
  monthOfYear?: string;
  startDate?: string;
  endDate?: string;
}

function toFormState(accounts: Account[], recurrence?: Recurrence): FormState {
  return {
    type: recurrence?.type ?? RecurrenceRequestType.EXPENSE,
    accountId: recurrence?.accountId ?? accounts[0]?.id ?? "",
    categoryId: recurrence?.categoryId ?? "",
    amountCents: recurrence?.amountCents ?? 0,
    description: recurrence?.description ?? "",
    note: recurrence?.note ?? "",
    frequency: recurrence?.frequency ?? RecurrenceFrequency.MONTHLY,
    dayOfMonth: recurrence?.dayOfMonth != null ? String(recurrence.dayOfMonth) : "",
    dayOfWeek: recurrence?.dayOfWeek ?? "",
    monthOfYear: recurrence?.monthOfYear != null ? String(recurrence.monthOfYear) : "",
    startDate: recurrence?.startDate ?? todayIso(),
    endDate: recurrence?.endDate ?? "",
    active: recurrence?.active ?? true,
  };
}

export function RecurrenceFormDialog({
  workspaceId,
  open,
  onOpenChange,
  recurrence,
  accounts,
  categories,
  scope,
  occurrenceDate,
}: RecurrenceFormDialogProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<FormState>(() => toFormState(accounts, recurrence));
  const [errors, setErrors] = useState<FormErrors>({});

  const isOccurrenceScoped = scope === RecurrenceScope.THIS_ONE;

  useEffect(() => {
    if (open) {
      setForm(toFormState(accounts, recurrence));
      setErrors({});
    }
    // accounts is intentionally excluded — it only seeds the default account
    // for a brand-new series and must not reset an in-progress edit if the
    // account list refetches in the background.
  }, [open, recurrence]);

  const createMutation = useCreateRecurrence();
  const updateMutation = useUpdateRecurrence();
  const isPending = createMutation.isPending || updateMutation.isPending;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const description = form.description.trim();
    const nextErrors: FormErrors = {};
    if (!description) nextErrors.description = t("validation.required");
    if (form.amountCents <= 0) nextErrors.amountCents = t("validation.amountPositive");
    if (!form.accountId) nextErrors.accountId = t("validation.required");
    if (!form.categoryId) nextErrors.categoryId = t("validation.required");
    if (!isOccurrenceScoped) {
      if (!form.startDate) nextErrors.startDate = t("validation.required");
      if (form.endDate && form.startDate && form.endDate < form.startDate) {
        nextErrors.endDate = t("recurrences.endDateBeforeStart");
      }
      if (form.frequency === RecurrenceFrequency.MONTHLY && !form.dayOfMonth) {
        nextErrors.dayOfMonth = t("validation.required");
      }
      if (form.frequency === RecurrenceFrequency.WEEKLY && !form.dayOfWeek) {
        nextErrors.dayOfWeek = t("validation.required");
      }
      if (form.frequency === RecurrenceFrequency.YEARLY) {
        if (!form.dayOfMonth) nextErrors.dayOfMonth = t("validation.required");
        if (!form.monthOfYear) nextErrors.monthOfYear = t("validation.required");
      }
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    const data: RecurrenceRequest = {
      type: form.type,
      accountId: form.accountId,
      categoryId: form.categoryId,
      amountCents: form.amountCents,
      description,
      note: form.note.trim() || null,
      frequency: form.frequency,
      dayOfMonth:
        form.frequency !== RecurrenceFrequency.WEEKLY && form.dayOfMonth
          ? Number(form.dayOfMonth)
          : null,
      dayOfWeek: form.frequency === RecurrenceFrequency.WEEKLY ? (form.dayOfWeek as DayOfWeek) : null,
      monthOfYear:
        form.frequency === RecurrenceFrequency.YEARLY && form.monthOfYear
          ? Number(form.monthOfYear)
          : null,
      startDate: form.startDate,
      endDate: form.endDate || null,
      active: form.active,
    };

    const options = {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: getListRecurrencesQueryKey(workspaceId) });
        toast.success(recurrence ? t("recurrences.updated") : t("recurrences.created"));
        onOpenChange(false);
      },
      onError: (error: unknown) => {
        if (
          error instanceof ApiError &&
          error.body.code === "VALIDATION_ERROR" &&
          error.body.fieldErrors?.length
        ) {
          const fieldErrors: FormErrors = {};
          for (const fieldError of error.body.fieldErrors) {
            fieldErrors[fieldError.field as keyof FormErrors] = translateFieldError(
              t,
              fieldError.code,
            );
          }
          setErrors((current) => ({ ...current, ...fieldErrors }));
        }
        toast.error(translateApiError(t, error));
      },
    };

    if (recurrence) {
      updateMutation.mutate(
        {
          workspaceId,
          recurrenceId: recurrence.id,
          data,
          params: scope ? { scope, occurrenceDate } : undefined,
        },
        options,
      );
    } else {
      createMutation.mutate({ workspaceId, data }, options);
    }
  }

  const categoryOpts = categoryOptionsForType(categories, form.type);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {!recurrence && t("recurrences.newTitle")}
            {recurrence && isOccurrenceScoped && t("recurrences.editOccurrenceTitle")}
            {recurrence && !isOccurrenceScoped && t("recurrences.editTitle")}
          </DialogTitle>
          <DialogDescription>
            {isOccurrenceScoped && occurrenceDate
              ? t("recurrences.editOccurrenceSubtitle", { date: formatDate(occurrenceDate) })
              : t("recurrences.formSubtitle")}
          </DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <SelectField
            id="recurrence-type"
            label={t("recurrences.type")}
            options={recurrenceTypeOptions(t)}
            value={form.type}
            onChange={(event) =>
              setForm({
                ...form,
                type: event.target.value as RecurrenceRequestType,
                categoryId: "",
              })
            }
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <SelectField
              id="recurrence-account"
              label={t("recurrences.account")}
              options={accountOptions(accounts)}
              value={form.accountId}
              error={errors.accountId}
              onChange={(event) => setForm({ ...form, accountId: event.target.value })}
            />
            <SelectField
              id="recurrence-category"
              label={t("recurrences.category")}
              placeholder={t("recurrences.categoryPlaceholder")}
              options={categoryOpts}
              value={form.categoryId}
              error={errors.categoryId}
              onChange={(event) => setForm({ ...form, categoryId: event.target.value })}
            />
          </div>
          <MoneyField
            id="recurrence-amount"
            label={t("recurrences.amount")}
            value={form.amountCents}
            error={errors.amountCents}
            onValueChange={(cents) => setForm({ ...form, amountCents: cents })}
          />
          <TextField
            id="recurrence-description"
            label={t("recurrences.description")}
            maxLength={255}
            autoComplete="off"
            value={form.description}
            error={errors.description}
            onChange={(event) => setForm({ ...form, description: event.target.value })}
          />
          <TextField
            id="recurrence-note"
            label={t("recurrences.note")}
            autoComplete="off"
            value={form.note}
            onChange={(event) => setForm({ ...form, note: event.target.value })}
          />

          {!isOccurrenceScoped && (
            <>
              <SelectField
                id="recurrence-frequency"
                label={t("recurrences.frequency")}
                options={frequencyOptions(t)}
                value={form.frequency}
                onChange={(event) =>
                  setForm({
                    ...form,
                    frequency: event.target.value as RecurrenceFrequency,
                    dayOfMonth: "",
                    dayOfWeek: "",
                    monthOfYear: "",
                  })
                }
              />

              {form.frequency !== RecurrenceFrequency.WEEKLY && (
                <TextField
                  id="recurrence-day-of-month"
                  label={t("recurrences.dayOfMonth")}
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={31}
                  value={form.dayOfMonth}
                  error={errors.dayOfMonth}
                  onChange={(event) => setForm({ ...form, dayOfMonth: event.target.value })}
                />
              )}
              {form.frequency === RecurrenceFrequency.WEEKLY && (
                <SelectField
                  id="recurrence-day-of-week"
                  label={t("recurrences.dayOfWeek")}
                  placeholder={t("recurrences.dayOfWeekPlaceholder")}
                  options={dayOfWeekOptions(t)}
                  value={form.dayOfWeek}
                  error={errors.dayOfWeek}
                  onChange={(event) => setForm({ ...form, dayOfWeek: event.target.value })}
                />
              )}
              {form.frequency === RecurrenceFrequency.YEARLY && (
                <TextField
                  id="recurrence-month-of-year"
                  label={t("recurrences.monthOfYear")}
                  type="number"
                  inputMode="numeric"
                  min={1}
                  max={12}
                  value={form.monthOfYear}
                  error={errors.monthOfYear}
                  onChange={(event) => setForm({ ...form, monthOfYear: event.target.value })}
                />
              )}

              <div className="grid gap-4 sm:grid-cols-2">
                <TextField
                  id="recurrence-start-date"
                  label={t("recurrences.startDate")}
                  type="date"
                  value={form.startDate}
                  error={errors.startDate}
                  onChange={(event) => setForm({ ...form, startDate: event.target.value })}
                />
                <TextField
                  id="recurrence-end-date"
                  label={t("recurrences.endDate")}
                  hint={!errors.endDate ? t("recurrences.endDateHint") : undefined}
                  type="date"
                  value={form.endDate}
                  error={errors.endDate}
                  onChange={(event) => setForm({ ...form, endDate: event.target.value })}
                />
              </div>

              {recurrence && (
                <label className="flex items-center gap-2 text-sm text-muted-foreground">
                  <input
                    type="checkbox"
                    className="size-4"
                    checked={form.active}
                    onChange={(event) => setForm({ ...form, active: event.target.checked })}
                  />
                  {t("recurrences.active")}
                </label>
              )}
            </>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending ? t("common.saving") : t("common.save")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
