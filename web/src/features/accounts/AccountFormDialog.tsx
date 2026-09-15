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
import {
  getListAccountsQueryKey,
  useCreateAccount,
  useUpdateAccount,
} from "@/lib/api/generated/accounts/accounts";
import {
  AccountType,
  ContributionPillar,
  type Account,
  type AccountRequest,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import { translateApiError } from "@/lib/errors";
import {
  accountTypeOptions,
  contributionPillarOptions,
  isContributionAccount,
} from "@/features/accounts/accountOptions";

interface AccountFormDialogProps {
  workspaceId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Present when editing; absent when creating. */
  account?: Account;
}

interface FormState {
  name: string;
  type: AccountType;
  initialBalanceCents: number;
  defaultContributionPillar: string;
}

const EMPTY_FORM: FormState = {
  name: "",
  type: AccountType.CHECKING,
  initialBalanceCents: 0,
  defaultContributionPillar: ContributionPillar.FINANCIAL_FREEDOM,
};

function toFormState(account?: Account): FormState {
  if (!account) return EMPTY_FORM;
  return {
    name: account.name,
    type: account.type,
    initialBalanceCents: account.initialBalanceCents,
    defaultContributionPillar:
      account.defaultContributionPillar ?? ContributionPillar.FINANCIAL_FREEDOM,
  };
}

export function AccountFormDialog({
  workspaceId,
  open,
  onOpenChange,
  account,
}: AccountFormDialogProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<FormState>(() => toFormState(account));
  const [nameError, setNameError] = useState<string | undefined>();

  // Reopening the dialog must always start from the entity being edited (or a
  // clean slate for creation) — otherwise the previous edit leaks into it.
  useEffect(() => {
    if (open) {
      setForm(toFormState(account));
      setNameError(undefined);
    }
  }, [open, account]);

  const createMutation = useCreateAccount();
  const updateMutation = useUpdateAccount();
  const isPending = createMutation.isPending || updateMutation.isPending;
  const showPillar = isContributionAccount(form);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = form.name.trim();
    if (!name) {
      setNameError(t("validation.required"));
      return;
    }
    setNameError(undefined);

    const data: AccountRequest = {
      name,
      type: form.type,
      initialBalanceCents: form.initialBalanceCents,
      // Only savings/investment accounts carry a default contribution pillar (R9).
      defaultContributionPillar: showPillar
        ? (form.defaultContributionPillar as ContributionPillar)
        : null,
    };

    const options = {
      onSuccess: () => {
        void queryClient.invalidateQueries({ queryKey: getListAccountsQueryKey(workspaceId) });
        toast.success(account ? t("accounts.updated") : t("accounts.created"));
        onOpenChange(false);
      },
      onError: (error: unknown) => {
        toast.error(translateApiError(t, error));
      },
    };

    if (account) {
      updateMutation.mutate({ workspaceId, accountId: account.id, data }, options);
    } else {
      createMutation.mutate({ workspaceId, data }, options);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="rounded-modal">
        <DialogHeader>
          <DialogTitle className="text-[15px] font-extrabold tracking-tight text-ink-primary">
            {account ? t("accounts.editTitle") : t("accounts.newTitle")}
          </DialogTitle>
          <DialogDescription className="text-ink-tertiary">
            {t("accounts.formSubtitle")}
          </DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <TextField
            id="account-name"
            label={t("accounts.name")}
            maxLength={120}
            autoComplete="off"
            value={form.name}
            error={nameError}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
          <SelectField
            id="account-type"
            label={t("accounts.type")}
            options={accountTypeOptions(t)}
            value={form.type}
            onChange={(event) => setForm({ ...form, type: event.target.value as AccountType })}
          />
          <MoneyField
            id="account-initial-balance"
            label={t("accounts.initialBalance")}
            value={form.initialBalanceCents}
            onValueChange={(cents) => setForm({ ...form, initialBalanceCents: cents })}
          />
          {showPillar && (
            <SelectField
              id="account-contribution-pillar"
              label={t("accounts.defaultContributionPillar")}
              hint={t("accounts.defaultContributionPillarHint")}
              options={contributionPillarOptions(t)}
              value={form.defaultContributionPillar}
              onChange={(event) =>
                setForm({ ...form, defaultContributionPillar: event.target.value })
              }
            />
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
