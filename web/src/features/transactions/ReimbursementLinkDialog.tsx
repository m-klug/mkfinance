import { useEffect, useMemo, useState } from "react";
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
import { MoneyField } from "@/components/form/fields";
import { useFormatCents } from "@/hooks/useFormatCents";
import { translateApiError } from "@/lib/errors";
import { formatDate } from "@/lib/format";
import { getListAccountsQueryKey } from "@/lib/api/generated/accounts/accounts";
import { getGetDashboardSummaryQueryKey, getGetPillarAllocationQueryKey } from "@/lib/api/generated/dashboard/dashboard";
import {
  getListReimbursablePendingQueryKey,
  getListReimbursementsQueryKey,
  useCreateReimbursement,
  useDeleteReimbursement,
  useListReimbursablePending,
  useListReimbursements,
} from "@/lib/api/generated/reimbursements/reimbursements";
import { getListTransactionsQueryKey } from "@/lib/api/generated/transactions/transactions";
import type { Reimbursement, Transaction } from "@/lib/api/generated/mKFinanceAPI.schemas";

interface ReimbursementLinkDialogProps {
  workspaceId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** The INCOME that was received (a PIX, cash in a CASH account...). */
  income: Transaction;
}

const NO_TRANSACTIONS: Transaction[] = [];
const NO_LINKS: Reimbursement[] = [];

/**
 * ADR-0002: links part of a received INCOME to the expenses it paid back. Lists every expense that
 * still expects money, of any month, and pre-fills oldest first up to what the income has left.
 */
export function ReimbursementLinkDialog({
  workspaceId,
  open,
  onOpenChange,
  income,
}: ReimbursementLinkDialogProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const queryClient = useQueryClient();
  const [amounts, setAmounts] = useState<Record<string, number>>({});
  const [saving, setSaving] = useState(false);

  const { data: pendingData, isPending: loadingPending } = useListReimbursablePending(workspaceId, {
    query: { enabled: open },
  });
  const { data: linksData } = useListReimbursements(workspaceId, income.id, {
    query: { enabled: open },
  });
  // Stable references: a fresh `[]` per render would re-run the pre-fill effect forever.
  const pending = pendingData ?? NO_TRANSACTIONS;
  const links = linksData ?? NO_LINKS;
  const createMutation = useCreateReimbursement();
  const deleteMutation = useDeleteReimbursement();

  const linkedCents = links.reduce((sum, link) => sum + link.amountCents, 0);
  const remainingCents = Math.max(0, income.amountCents - linkedCents);

  // Pre-fill greedily, oldest expense first, so the common case is one click.
  const prefill = useMemo(() => {
    const next: Record<string, number> = {};
    let left = remainingCents;
    for (const expense of pending) {
      const take = Math.min(left, expense.pendingReimbursementCents ?? 0);
      if (take > 0) next[expense.id] = take;
      left -= take;
    }
    return next;
  }, [pending, remainingCents]);

  useEffect(() => {
    if (open) setAmounts(prefill);
  }, [open, prefill]);

  const totalCents = Object.values(amounts).reduce((sum, value) => sum + value, 0);
  const overPending = pending.some(
    (expense) => (amounts[expense.id] ?? 0) > (expense.pendingReimbursementCents ?? 0),
  );
  const overIncome = totalCents > remainingCents;
  const canSave = totalCents > 0 && !overPending && !overIncome && !saving;

  function invalidate() {
    void queryClient.invalidateQueries({ queryKey: getListTransactionsQueryKey(workspaceId) });
    void queryClient.invalidateQueries({ queryKey: getListAccountsQueryKey(workspaceId) });
    void queryClient.invalidateQueries({ queryKey: getListReimbursablePendingQueryKey(workspaceId) });
    void queryClient.invalidateQueries({ queryKey: getListReimbursementsQueryKey(workspaceId, income.id) });
    void queryClient.invalidateQueries({ queryKey: getGetDashboardSummaryQueryKey(workspaceId) });
    void queryClient.invalidateQueries({ queryKey: getGetPillarAllocationQueryKey(workspaceId) });
  }

  async function handleSave() {
    setSaving(true);
    try {
      for (const expense of pending) {
        const amountCents = amounts[expense.id] ?? 0;
        if (amountCents <= 0) continue;
        await createMutation.mutateAsync({
          workspaceId,
          transactionId: income.id,
          data: { expenseTransactionId: expense.id, amountCents },
        });
      }
      toast.success(t("reimbursement.linked"));
      onOpenChange(false);
    } catch (error) {
      toast.error(translateApiError(t, error));
    } finally {
      setSaving(false);
      invalidate();
    }
  }

  function handleUnlink(reimbursementId: string) {
    deleteMutation.mutate(
      { workspaceId, reimbursementId },
      {
        onSuccess: () => {
          invalidate();
          toast.success(t("reimbursement.unlinked"));
        },
        onError: (error) => toast.error(translateApiError(t, error)),
      },
    );
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("reimbursement.linkTitle")}</DialogTitle>
          <DialogDescription>
            {t("reimbursement.linkDescription", {
              description: income.description,
              amount: formatCents(income.amountCents),
              remaining: formatCents(remainingCents),
            })}
          </DialogDescription>
        </DialogHeader>

        {links.length > 0 && (
          <div className="flex flex-col gap-2">
            <span className="text-[11px] font-bold tracking-wide text-ink-label uppercase">
              {t("reimbursement.existingLinks")}
            </span>
            <ul className="flex flex-col gap-1.5">
              {links.map((link) => (
                <li key={link.id} className="flex items-center justify-between gap-2 text-sm">
                  <span className="text-ink-primary">
                    {formatCents(link.amountCents)}{" "}
                    <span className="text-xs text-ink-secondary">
                      · {formatDate(link.createdAt.slice(0, 10))}
                    </span>
                  </span>
                  <Button
                    type="button"
                    variant="secondary"
                    size="sm"
                    disabled={deleteMutation.isPending}
                    onClick={() => handleUnlink(link.id)}
                  >
                    {t("reimbursement.unlink")}
                  </Button>
                </li>
              ))}
            </ul>
          </div>
        )}

        <div className="flex flex-col gap-3">
          <span className="text-[11px] font-bold tracking-wide text-ink-label uppercase">
            {t("reimbursement.pendingExpenses")}
          </span>
          {loadingPending && <p className="text-ink-secondary">{t("common.loading")}</p>}
          {!loadingPending && pending.length === 0 && (
            <p className="text-sm text-ink-secondary">{t("reimbursement.noPending")}</p>
          )}
          {pending.map((expense) => (
            <div key={expense.id} className="flex flex-col gap-1">
              <div className="flex items-baseline justify-between gap-2 text-sm">
                <span className="font-bold text-ink-primary">{expense.description}</span>
                <span className="text-xs text-ink-secondary">{formatDate(expense.date)}</span>
              </div>
              <MoneyField
                id={`reimbursement-${expense.id}`}
                label={t("reimbursement.amountForExpense", {
                  pending: formatCents(expense.pendingReimbursementCents ?? 0),
                })}
                value={amounts[expense.id] ?? 0}
                error={
                  (amounts[expense.id] ?? 0) > (expense.pendingReimbursementCents ?? 0)
                    ? t("reimbursement.overPending")
                    : undefined
                }
                onValueChange={(cents) => setAmounts({ ...amounts, [expense.id]: cents })}
              />
            </div>
          ))}
          {overIncome && (
            <p className="text-xs text-destructive">
              {t("reimbursement.overIncome", { remaining: formatCents(remainingCents) })}
            </p>
          )}
        </div>

        <DialogFooter>
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
            {t("common.cancel")}
          </Button>
          <Button type="button" disabled={!canSave} onClick={() => void handleSave()}>
            {saving ? t("common.saving") : t("reimbursement.confirm", { total: formatCents(totalCents) })}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
