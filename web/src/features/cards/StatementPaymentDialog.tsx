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
import { formatCents } from "@/lib/format";
import { translateApiError } from "@/lib/errors";
import { usePayStatement } from "@/lib/api/generated/statements/statements";
import type { Account, StatementPaymentRequest } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { todayIsoDate } from "@/features/cards/cardOptions";

interface StatementPaymentDialogProps {
  workspaceId: string;
  statementId: string;
  remainingCents: number;
  accounts: Account[];
  defaultAccountId?: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onPaid: () => void;
}

export function StatementPaymentDialog({
  workspaceId,
  statementId,
  remainingCents,
  accounts,
  defaultAccountId,
  open,
  onOpenChange,
  onPaid,
}: StatementPaymentDialogProps) {
  const { t } = useTranslation();
  const [accountId, setAccountId] = useState("");
  const [amountCents, setAmountCents] = useState(0);
  const [date, setDate] = useState(todayIsoDate);
  const [errors, setErrors] = useState<{ amount?: string; account?: string }>({});

  // Full payment is the common case, so the amount opens pre-filled with the
  // whole remaining balance — the user only touches it for a partial payment.
  useEffect(() => {
    if (!open) return;
    setAmountCents(remainingCents);
    setDate(todayIsoDate());
    setErrors({});
    const preferred =
      defaultAccountId && accounts.some((account) => account.id === defaultAccountId)
        ? defaultAccountId
        : (accounts[0]?.id ?? "");
    setAccountId(preferred);
  }, [open, remainingCents, defaultAccountId, accounts]);

  const payMutation = usePayStatement();

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors: { amount?: string; account?: string } = {};
    if (amountCents <= 0) nextErrors.amount = t("validation.amountPositive");
    else if (amountCents > remainingCents) nextErrors.amount = t("statement.paymentExceeds");
    if (!accountId) nextErrors.account = t("validation.required");
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    const data: StatementPaymentRequest = { accountId, amountCents, paymentDate: date };
    payMutation.mutate(
      { workspaceId, statementId, data },
      {
        onSuccess: () => {
          toast.success(t("statement.paymentRegistered"));
          onPaid();
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
          <DialogTitle>{t("statement.payTitle")}</DialogTitle>
          <DialogDescription>
            {t("statement.paySubtitle", { amount: formatCents(remainingCents) })}
          </DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <SelectField
            id="payment-account"
            label={t("statement.paymentAccount")}
            placeholder={t("transactions.categoryPlaceholder")}
            options={accounts.map((account) => ({ value: account.id, label: account.name }))}
            value={accountId}
            error={errors.account}
            onChange={(event) => setAccountId(event.target.value)}
          />
          <MoneyField
            id="payment-amount"
            label={t("statement.paymentAmount")}
            hint={t("statement.paymentAmountHint")}
            value={amountCents}
            error={errors.amount}
            onValueChange={setAmountCents}
          />
          <TextField
            id="payment-date"
            label={t("statement.paymentDate")}
            type="date"
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={payMutation.isPending}>
              {payMutation.isPending ? t("common.saving") : t("statement.payConfirm")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
