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
import { MoneyField, TextField } from "@/components/form/fields";
import { formatCents } from "@/lib/format";
import { translateApiError } from "@/lib/errors";
import { useCreateVaultYield } from "@/lib/api/generated/vaults/vaults";
import type { Vault, VaultYieldRequest } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { todayIso } from "@/features/transactions/transactionUtils";
import { yieldPreviewShares } from "@/features/vaults/vaultOptions";

interface VaultYieldDialogProps {
  workspaceId: string;
  accountId: string;
  /** The account's own vaults (composition.vaults) — the preview's base, mirroring §4.5. */
  vaults: Vault[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onCreated: () => void;
}

/**
 * Books the month's yield as an INCOME transaction on the account (§4.5) and shows, before
 * confirming, how it would split across the vaults — a local mirror of `YieldDistributor`
 * (`yieldPreviewShares`), not the real split. The real one is `VaultYield.shares`, computed by the
 * server at the instant of confirmation from whatever the vaults' balances are *then*; this preview
 * can only ever be as fresh as the composition already loaded on the page, so the dialog is explicit
 * that the number it shows may not be the final one.
 */
export function VaultYieldDialog({
  workspaceId,
  accountId,
  vaults,
  open,
  onOpenChange,
  onCreated,
}: VaultYieldDialogProps) {
  const { t } = useTranslation();
  const [amountCents, setAmountCents] = useState(0);
  const [date, setDate] = useState(todayIso);
  const [description, setDescription] = useState("");
  const [amountError, setAmountError] = useState<string | undefined>();

  useEffect(() => {
    if (open) {
      setAmountCents(0);
      setDate(todayIso());
      setDescription("");
      setAmountError(undefined);
    }
  }, [open]);

  const createMutation = useCreateVaultYield();
  const preview = yieldPreviewShares(vaults, amountCents);
  const previewedCents = preview.reduce((sum, share) => sum + share.cents, 0);
  const unallocatedPreviewCents = amountCents - previewedCents;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (amountCents <= 0) {
      setAmountError(t("validation.amountPositive"));
      return;
    }
    setAmountError(undefined);

    const data: VaultYieldRequest = {
      amountCents,
      date,
      description: description.trim() || null,
    };

    createMutation.mutate(
      { workspaceId, accountId, data },
      {
        onSuccess: () => {
          onCreated();
          toast.success(t("vaults.yieldCreated"));
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
          <DialogTitle>{t("vaults.yieldTitle")}</DialogTitle>
          <DialogDescription>{t("vaults.yieldSubtitle")}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <MoneyField
            id="vault-yield-amount"
            label={t("vaults.yieldAmount")}
            value={amountCents}
            error={amountError}
            autoFocus
            onValueChange={setAmountCents}
          />
          <TextField
            id="vault-yield-date"
            label={t("vaults.yieldDate")}
            type="date"
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
          <TextField
            id="vault-yield-description"
            label={t("vaults.yieldDescription")}
            placeholder={t("vaults.yieldDescriptionPlaceholder")}
            maxLength={255}
            autoComplete="off"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />

          {amountCents > 0 && (
            <div className="flex flex-col gap-2 rounded-md border p-3 text-sm">
              <p className="font-medium">{t("vaults.yieldPreviewTitle")}</p>
              {preview.length === 0 ? (
                <p className="text-muted-foreground">{t("vaults.yieldPreviewEmpty")}</p>
              ) : (
                <ul className="flex flex-col gap-1">
                  {preview.map((share) => (
                    <li key={share.vaultId} className="flex justify-between tabular-nums">
                      <span>{share.name}</span>
                      <span>{formatCents(share.cents)}</span>
                    </li>
                  ))}
                  {unallocatedPreviewCents > 0 && (
                    <li className="flex justify-between tabular-nums text-muted-foreground">
                      <span>{t("vaults.yieldPreviewUnallocated")}</span>
                      <span>{formatCents(unallocatedPreviewCents)}</span>
                    </li>
                  )}
                </ul>
              )}
              <p className="text-xs text-muted-foreground">{t("vaults.yieldPreviewHint")}</p>
            </div>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? t("common.saving") : t("vaults.yieldConfirm")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
