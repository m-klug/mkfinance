import { useEffect, useState, type FormEvent } from "react";
import { useTranslation } from "react-i18next";
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
import { useCreateVault, useUpdateVault } from "@/lib/api/generated/vaults/vaults";
import { ContributionPillar, type Vault, type VaultRequest } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { contributionPillarOptions } from "@/features/accounts/accountOptions";
import { DEFAULT_VAULT_COLOR } from "@/features/vaults/vaultOptions";

interface VaultFormDialogProps {
  workspaceId: string;
  accountId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Present when editing; absent when creating. */
  vault?: Vault;
  onSaved: () => void;
}

interface FormState {
  name: string;
  contributionPillar: ContributionPillar;
  targetAmountCents: number;
  targetDate: string;
  color: string;
}

const EMPTY_FORM: FormState = {
  name: "",
  contributionPillar: ContributionPillar.GOALS,
  targetAmountCents: 0,
  targetDate: "",
  color: DEFAULT_VAULT_COLOR,
};

function toFormState(vault?: Vault): FormState {
  if (!vault) return EMPTY_FORM;
  return {
    name: vault.name,
    contributionPillar: vault.contributionPillar,
    targetAmountCents: vault.targetAmountCents ?? 0,
    targetDate: vault.targetDate ?? "",
    color: vault.color ?? DEFAULT_VAULT_COLOR,
  };
}

export function VaultFormDialog({
  workspaceId,
  accountId,
  open,
  onOpenChange,
  vault,
  onSaved,
}: VaultFormDialogProps) {
  const { t } = useTranslation();
  const [form, setForm] = useState<FormState>(() => toFormState(vault));
  const [nameError, setNameError] = useState<string | undefined>();

  // Reopening must always start from the entity being edited (or a clean
  // slate for creation), never leak the previous edit into the fields.
  useEffect(() => {
    if (open) {
      setForm(toFormState(vault));
      setNameError(undefined);
    }
  }, [open, vault]);

  const createMutation = useCreateVault();
  const updateMutation = useUpdateVault();
  const isPending = createMutation.isPending || updateMutation.isPending;
  // A deadline alone has nothing to project (§4.6): the field only makes
  // sense once a target amount exists, so it stays hidden until then instead
  // of letting the user build a combination the server would reject.
  const hasTarget = form.targetAmountCents > 0;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = form.name.trim();
    if (!name) {
      setNameError(t("validation.required"));
      return;
    }
    setNameError(undefined);

    const data: VaultRequest = {
      name,
      contributionPillar: form.contributionPillar,
      targetAmountCents: hasTarget ? form.targetAmountCents : null,
      targetDate: hasTarget && form.targetDate ? form.targetDate : null,
      color: form.color || null,
    };

    const options = {
      onSuccess: () => {
        onSaved();
        toast.success(vault ? t("vaults.updated") : t("vaults.created"));
        onOpenChange(false);
      },
      onError: (error: unknown) => {
        toast.error(translateApiError(t, error));
      },
    };

    if (vault) {
      updateMutation.mutate({ workspaceId, vaultId: vault.id, data }, options);
    } else {
      createMutation.mutate({ workspaceId, accountId, data }, options);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="rounded-modal">
        <DialogHeader>
          <DialogTitle className="text-[15px] font-extrabold tracking-tight text-ink-primary">
            {vault ? t("vaults.editTitle") : t("vaults.newTitle")}
          </DialogTitle>
          <DialogDescription className="text-ink-tertiary">
            {t("vaults.formSubtitle")}
          </DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <TextField
            id="vault-name"
            label={t("vaults.name")}
            maxLength={120}
            autoComplete="off"
            value={form.name}
            error={nameError}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
          />
          <SelectField
            id="vault-pillar"
            label={t("vaults.pillar")}
            options={contributionPillarOptions(t)}
            value={form.contributionPillar}
            onChange={(event) =>
              setForm({ ...form, contributionPillar: event.target.value as ContributionPillar })
            }
          />
          <MoneyField
            id="vault-target-amount"
            label={t("vaults.targetAmount")}
            hint={t("vaults.targetAmountHint")}
            value={form.targetAmountCents}
            onValueChange={(cents) =>
              setForm({
                ...form,
                targetAmountCents: cents,
                targetDate: cents > 0 ? form.targetDate : "",
              })
            }
          />
          {hasTarget && (
            <TextField
              id="vault-target-date"
              label={t("vaults.targetDate")}
              hint={t("vaults.targetDateHint")}
              type="date"
              value={form.targetDate}
              onChange={(event) => setForm({ ...form, targetDate: event.target.value })}
            />
          )}
          <TextField
            id="vault-color"
            label={t("vaults.color")}
            type="color"
            value={form.color}
            onChange={(event) => setForm({ ...form, color: event.target.value })}
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
