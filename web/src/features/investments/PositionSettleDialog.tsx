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
import { formatCents, formatDate } from "@/lib/format";
import { translateApiError } from "@/lib/errors";
import { useListTransactions } from "@/lib/api/generated/transactions/transactions";
import { getListAccountsQueryKey } from "@/lib/api/generated/accounts/accounts";
import { getListVaultsQueryKey } from "@/lib/api/generated/vaults/vaults";
import {
  getGetInvestmentSummaryQueryKey,
  getListPositionsQueryKey,
  useSettlePosition,
} from "@/lib/api/generated/investments/investments";
import type { Position } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { todayIso } from "@/features/transactions/transactionUtils";

interface PositionSettleDialogProps {
  workspaceId: string;
  /** The position being paid; null keeps the dialog closed. */
  position: Position | null;
  onClose: () => void;
}

/**
 * Books the payment of a title: the user tells when the credit fell and how much, net, and the app
 * books only the yield (received minus the principal). The money that fell in checking is not
 * recorded here — it comes from the bank statement import or a manual entry — but the transfer can be
 * linked, so the position remembers where the money went.
 */
export function PositionSettleDialog({ workspaceId, position, onClose }: PositionSettleDialogProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const open = position !== null;
  const [date, setDate] = useState(todayIso);
  const [receivedCents, setReceivedCents] = useState(0);
  const [returnTransferId, setReturnTransferId] = useState("");
  const [error, setError] = useState<string | undefined>();

  useEffect(() => {
    if (position) {
      setDate(todayIso());
      // The estimate is the natural first guess: the user corrects it with what the bank showed.
      setReceivedCents(position.estimate?.atMaturity.netCents ?? position.principalCents);
      setReturnTransferId("");
      setError(undefined);
    }
  }, [position]);

  // Candidate transfers back: outgoing legs of the position's own account since it was bought.
  const { data: transfers } = useListTransactions(
    workspaceId,
    { accountId: position?.accountId, type: "TRANSFER", from: position?.purchaseDate, size: 100 },
    { query: { enabled: open } },
  );
  const candidates = (transfers?.content ?? []).filter((tx) => tx.transferDirection === "OUT");

  const settleMutation = useSettlePosition();

  if (!position) return null;
  const yieldCents = receivedCents - position.principalCents;
  const noVault = !position.vaultId;

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!position) return;
    if (receivedCents < position.principalCents) {
      setError(t("investments.settle.belowPrincipal", { principal: formatCents(position.principalCents) }));
      return;
    }
    setError(undefined);
    settleMutation.mutate(
      {
        workspaceId,
        positionId: position.id,
        data: { settledDate: date, receivedCents, ...(returnTransferId ? { returnTransferTransactionId: returnTransferId } : {}) },
      },
      {
        onSuccess: () => {
          void queryClient.invalidateQueries({ queryKey: getListPositionsQueryKey(workspaceId) });
          void queryClient.invalidateQueries({ queryKey: getGetInvestmentSummaryQueryKey(workspaceId) });
          void queryClient.invalidateQueries({ queryKey: getListAccountsQueryKey(workspaceId) });
          void queryClient.invalidateQueries({ queryKey: getListVaultsQueryKey(workspaceId, position.accountId) });
          toast.success(t("investments.settle.done"));
          onClose();
        },
        onError: (e) => {
          toast.error(translateApiError(t, e));
        },
      },
    );
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto rounded-modal">
        <DialogHeader>
          <DialogTitle className="text-[15px] font-extrabold tracking-tight text-ink-primary">
            {t("investments.settle.title")}
          </DialogTitle>
          <DialogDescription className="text-ink-tertiary">{t("investments.settle.subtitle")}</DialogDescription>
        </DialogHeader>

        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <TextField
            id="settle-date"
            label={t("investments.settle.date")}
            type="date"
            value={date}
            max={todayIso()}
            onChange={(event) => setDate(event.target.value)}
          />
          <MoneyField
            id="settle-received"
            label={t("investments.settle.received")}
            hint={
              position.estimate
                ? t("investments.settle.estimateHint", { amount: formatCents(position.estimate.atMaturity.netCents) })
                : t("investments.settle.noEstimateHint")
            }
            value={receivedCents}
            error={error}
            autoFocus
            onValueChange={setReceivedCents}
          />

          <div className="flex flex-col gap-1 rounded-field bg-field p-3 text-sm">
            <div className="flex justify-between gap-3">
              <span className="text-ink-secondary">{t("investments.settle.principal")}</span>
              <span className="font-bold tabular-nums text-ink-primary">{formatCents(position.principalCents)}</span>
            </div>
            <div className="flex justify-between gap-3">
              <span className="text-ink-secondary">{t("investments.settle.yield")}</span>
              <span className="font-bold tabular-nums text-ink-primary" data-testid="settle-yield">
                {formatCents(Math.max(yieldCents, 0))}
              </span>
            </div>
            <p className="pt-1 text-[11px] text-ink-tertiary">
              {noVault ? t("investments.settle.yieldGoesUnallocated") : t("investments.settle.yieldGoesTo", { vault: position.vaultName })}
            </p>
          </div>

          <SelectField
            id="settle-return-transfer"
            label={t("investments.settle.returnTransfer")}
            hint={t("investments.settle.returnTransferHint")}
            placeholder={t("investments.settle.noLink")}
            value={returnTransferId}
            onChange={(event) => setReturnTransferId(event.target.value)}
            options={candidates.map((tx) => ({
              value: tx.id,
              label: `${formatDate(tx.date)} · ${formatCents(tx.amountCents)} · ${tx.description}`,
            }))}
          />

          {noVault && (
            <p className="rounded-field bg-alert-bg p-3 text-xs text-alert-ink">{t("investments.settle.noVaultWarning")}</p>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={settleMutation.isPending}>
              {t("investments.settle.confirm")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
