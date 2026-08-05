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
import { useReallocateVault } from "@/lib/api/generated/vaults/vaults";
import type { Vault, VaultReallocationRequest } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { todayIso } from "@/features/transactions/transactionUtils";
import { selectValueToVaultId, vaultSelectOptions } from "@/features/vaults/vaultOptions";

interface VaultReallocateDialogProps {
  workspaceId: string;
  /** Only non-archived vaults are offered (an archived one must already be empty). */
  vaults: Vault[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onReallocated: () => void;
}

interface Errors {
  vaults?: string;
  amount?: string;
}

/**
 * Moves money between vaults, or between a vault and the unallocated slice
 * (either side can be "Não alocado", value=""; §4.7). This is the whole
 * onboarding path for money that already sits in the account before any
 * vault existed, and it is also how the last vault gets emptied before
 * archiving — no account balance moves and no pillar is consumed.
 */
export function VaultReallocateDialog({
  workspaceId,
  vaults,
  open,
  onOpenChange,
  onReallocated,
}: VaultReallocateDialogProps) {
  const { t } = useTranslation();
  const [fromValue, setFromValue] = useState("");
  const [toValue, setToValue] = useState("");
  const [amountCents, setAmountCents] = useState(0);
  const [date, setDate] = useState(todayIso);
  const [description, setDescription] = useState("");
  const [errors, setErrors] = useState<Errors>({});

  useEffect(() => {
    if (open) {
      setFromValue("");
      setToValue("");
      setAmountCents(0);
      setDate(todayIso());
      setDescription("");
      setErrors({});
    }
  }, [open]);

  const reallocateMutation = useReallocateVault();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors: Errors = {};
    if (fromValue === "" && toValue === "") {
      nextErrors.vaults = t("vaults.reallocateBothUnallocated");
    } else if (fromValue !== "" && fromValue === toValue) {
      nextErrors.vaults = t("vaults.reallocateSameVault");
    }
    if (amountCents <= 0) nextErrors.amount = t("validation.amountPositive");
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    const data: VaultReallocationRequest = {
      fromVaultId: selectValueToVaultId(fromValue),
      toVaultId: selectValueToVaultId(toValue),
      amountCents,
      date,
      description: description.trim() || null,
    };

    reallocateMutation.mutate(
      { workspaceId, data },
      {
        onSuccess: () => {
          onReallocated();
          toast.success(t("vaults.reallocated"));
          onOpenChange(false);
        },
        onError: (error) => {
          toast.error(translateApiError(t, error));
        },
      },
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("vaults.reallocateTitle")}</DialogTitle>
          <DialogDescription>{t("vaults.reallocateSubtitle")}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <SelectField
              id="reallocate-from"
              label={t("vaults.reallocateFrom")}
              options={vaultSelectOptions(t, vaults)}
              value={fromValue}
              error={errors.vaults}
              onChange={(event) => setFromValue(event.target.value)}
            />
            <SelectField
              id="reallocate-to"
              label={t("vaults.reallocateTo")}
              options={vaultSelectOptions(t, vaults)}
              value={toValue}
              onChange={(event) => setToValue(event.target.value)}
            />
          </div>
          <MoneyField
            id="reallocate-amount"
            label={t("vaults.reallocateAmount")}
            value={amountCents}
            error={errors.amount}
            onValueChange={setAmountCents}
          />
          <TextField
            id="reallocate-date"
            label={t("vaults.reallocateDate")}
            type="date"
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
          <TextField
            id="reallocate-description"
            label={t("vaults.reallocateDescription")}
            maxLength={255}
            autoComplete="off"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={reallocateMutation.isPending}>
              {reallocateMutation.isPending ? t("common.saving") : t("vaults.reallocateConfirm")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
