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
import { translateApiError } from "@/lib/errors";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import { useListAccounts } from "@/lib/api/generated/accounts/accounts";
import {
  getListCardsQueryKey,
  useCreateCard,
  useUpdateCard,
} from "@/lib/api/generated/cards/cards";
import type { Card, CardRequest } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { activeAccounts } from "@/features/transactions/transactionUtils";
import { STATEMENT_DAYS } from "@/features/cards/cardOptions";

interface CardFormDialogProps {
  workspaceId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Present when editing; absent when creating. */
  card?: Card;
}

interface FormState {
  name: string;
  brand: string;
  limitCents: number;
  closingDay: number;
  dueDay: number;
  defaultPaymentAccountId: string;
}

const EMPTY_FORM: FormState = {
  name: "",
  brand: "",
  limitCents: 0,
  closingDay: 1,
  dueDay: 10,
  defaultPaymentAccountId: "",
};

function toFormState(card?: Card): FormState {
  if (!card) return EMPTY_FORM;
  return {
    name: card.name,
    brand: card.brand ?? "",
    limitCents: card.limitCents,
    closingDay: card.closingDay,
    dueDay: card.dueDay,
    defaultPaymentAccountId: card.defaultPaymentAccountId ?? "",
  };
}

function dayOptions() {
  return STATEMENT_DAYS.map((day) => ({ value: String(day), label: String(day) }));
}

export function CardFormDialog({ workspaceId, open, onOpenChange, card }: CardFormDialogProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<FormState>(() => toFormState(card));
  const [nameError, setNameError] = useState<string | undefined>();

  const currentWorkspaceId = useCurrentWorkspaceId();
  const { data: accountsData } = useListAccounts(currentWorkspaceId ?? "", undefined, {
    query: { enabled: Boolean(currentWorkspaceId) && open },
  });
  const accounts = activeAccounts(accountsData ?? []);

  // Reopening must always start from the entity being edited (or a clean slate),
  // never leak the previous edit into the fields.
  useEffect(() => {
    if (open) {
      setForm(toFormState(card));
      setNameError(undefined);
    }
  }, [open, card]);

  const createMutation = useCreateCard();
  const updateMutation = useUpdateCard();
  const isPending = createMutation.isPending || updateMutation.isPending;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = form.name.trim();
    if (!name) {
      setNameError(t("validation.required"));
      return;
    }
    setNameError(undefined);

    const data: CardRequest = {
      name,
      brand: form.brand.trim() || null,
      limitCents: form.limitCents,
      closingDay: form.closingDay,
      dueDay: form.dueDay,
      defaultPaymentAccountId: form.defaultPaymentAccountId || null,
    };

    const options = {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: getListCardsQueryKey(workspaceId) });
        toast.success(card ? t("cards.updated") : t("cards.created"));
        onOpenChange(false);
      },
      onError: (error: unknown) => {
        toast.error(translateApiError(t, error));
      },
    };

    if (card) {
      updateMutation.mutate({ workspaceId, cardId: card.id, data }, options);
    } else {
      createMutation.mutate({ workspaceId, data }, options);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{card ? t("cards.editTitle") : t("cards.newTitle")}</DialogTitle>
          <DialogDescription>{t("cards.formSubtitle")}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <TextField
            id="card-name"
            label={t("cards.name")}
            maxLength={120}
            autoComplete="off"
            value={form.name}
            error={nameError}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
          <TextField
            id="card-brand"
            label={t("cards.brand")}
            maxLength={60}
            autoComplete="off"
            value={form.brand}
            onChange={(event) => setForm({ ...form, brand: event.target.value })}
          />
          <MoneyField
            id="card-limit"
            label={t("cards.limit")}
            value={form.limitCents}
            onValueChange={(cents) => setForm({ ...form, limitCents: cents })}
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <SelectField
              id="card-closing-day"
              label={t("cards.closingDay")}
              options={dayOptions()}
              value={String(form.closingDay)}
              onChange={(event) => setForm({ ...form, closingDay: Number(event.target.value) })}
            />
            <SelectField
              id="card-due-day"
              label={t("cards.dueDay")}
              options={dayOptions()}
              value={String(form.dueDay)}
              onChange={(event) => setForm({ ...form, dueDay: Number(event.target.value) })}
            />
          </div>
          <SelectField
            id="card-payment-account"
            label={t("cards.defaultPaymentAccount")}
            hint={t("cards.defaultPaymentAccountHint")}
            placeholder={t("cards.noPaymentAccount")}
            options={accounts.map((account) => ({ value: account.id, label: account.name }))}
            value={form.defaultPaymentAccountId}
            onChange={(event) =>
              setForm({ ...form, defaultPaymentAccountId: event.target.value })
            }
          />
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
