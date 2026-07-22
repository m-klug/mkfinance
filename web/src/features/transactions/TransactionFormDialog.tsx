import { useEffect, useMemo, useState, type FormEvent } from "react";
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
import { getListAccountsQueryKey } from "@/lib/api/generated/accounts/accounts";
import {
  getListTransactionsQueryKey,
  useUpdateTransaction,
} from "@/lib/api/generated/transactions/transactions";
import {
  CategoryType,
  TransactionRequestType,
  TransactionStatus,
  TransactionType,
  type Account,
  type Category,
  type Transaction,
  type TransactionRequest,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import { categoryOptions } from "@/features/transactions/transactionUtils";

interface TransactionFormDialogProps {
  workspaceId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Income/expense only — transfer legs are edited as a pair by the API. */
  transaction: Transaction;
  accounts: Account[];
  categories: Category[];
}

interface FormState {
  accountId: string;
  categoryId: string;
  amountCents: number;
  date: string;
  description: string;
  note: string;
  status: TransactionStatus;
}

function toFormState(transaction: Transaction): FormState {
  return {
    accountId: transaction.accountId,
    categoryId: transaction.categoryId ?? "",
    amountCents: transaction.amountCents,
    date: transaction.date,
    description: transaction.description,
    note: transaction.note ?? "",
    status: transaction.status,
  };
}

export function TransactionFormDialog({
  workspaceId,
  open,
  onOpenChange,
  transaction,
  accounts,
  categories,
}: TransactionFormDialogProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<FormState>(() => toFormState(transaction));
  const [errors, setErrors] = useState<{ amount?: string; description?: string; category?: string }>(
    {},
  );

  useEffect(() => {
    if (open) {
      setForm(toFormState(transaction));
      setErrors({});
    }
  }, [open, transaction]);

  const updateMutation = useUpdateTransaction();

  const availableCategories = useMemo(() => {
    const wanted =
      transaction.type === TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
    return categories.filter((category) => category.type === wanted && !category.system);
  }, [categories, transaction.type]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextErrors: { amount?: string; description?: string; category?: string } = {};
    if (form.amountCents <= 0) nextErrors.amount = t("validation.amountPositive");
    if (!form.description.trim()) nextErrors.description = t("validation.required");
    if (!form.categoryId) nextErrors.category = t("validation.required");
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    const data: TransactionRequest = {
      accountId: form.accountId,
      categoryId: form.categoryId,
      type:
        transaction.type === TransactionType.INCOME
          ? TransactionRequestType.INCOME
          : TransactionRequestType.EXPENSE,
      amountCents: form.amountCents,
      date: form.date,
      description: form.description.trim(),
      note: form.note.trim() || null,
      status: form.status,
    };

    updateMutation.mutate(
      { workspaceId, transactionId: transaction.id, data },
      {
        onSuccess: () => {
          void queryClient.invalidateQueries({
            queryKey: getListTransactionsQueryKey(workspaceId),
          });
          void queryClient.invalidateQueries({ queryKey: getListAccountsQueryKey(workspaceId) });
          toast.success(t("transactions.updated"));
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
          <DialogTitle>{t("transactions.editTitle")}</DialogTitle>
          <DialogDescription>{t(`transactionType.${transaction.type}`)}</DialogDescription>
        </DialogHeader>
        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <MoneyField
            id="transaction-amount"
            label={t("transactions.amount")}
            value={form.amountCents}
            error={errors.amount}
            onValueChange={(cents) => setForm({ ...form, amountCents: cents })}
          />
          <TextField
            id="transaction-description"
            label={t("transactions.description")}
            maxLength={255}
            autoComplete="off"
            value={form.description}
            error={errors.description}
            onChange={(event) => setForm({ ...form, description: event.target.value })}
          />
          <SelectField
            id="transaction-category"
            label={t("transactions.category")}
            placeholder={t("transactions.categoryPlaceholder")}
            options={categoryOptions(availableCategories)}
            value={form.categoryId}
            error={errors.category}
            onChange={(event) => setForm({ ...form, categoryId: event.target.value })}
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <SelectField
              id="transaction-account"
              label={t("transactions.account")}
              options={accounts.map((account) => ({ value: account.id, label: account.name }))}
              value={form.accountId}
              onChange={(event) => setForm({ ...form, accountId: event.target.value })}
            />
            <TextField
              id="transaction-date"
              label={t("transactions.date")}
              type="date"
              value={form.date}
              onChange={(event) => setForm({ ...form, date: event.target.value })}
            />
          </div>
          <SelectField
            id="transaction-status"
            label={t("transactions.status")}
            options={[
              { value: TransactionStatus.SETTLED, label: t("transactionStatus.SETTLED") },
              { value: TransactionStatus.PENDING, label: t("transactionStatus.PENDING") },
            ]}
            value={form.status}
            onChange={(event) =>
              setForm({ ...form, status: event.target.value as TransactionStatus })
            }
          />
          <TextField
            id="transaction-note"
            label={t("transactions.note")}
            autoComplete="off"
            value={form.note}
            onChange={(event) => setForm({ ...form, note: event.target.value })}
          />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={updateMutation.isPending}>
              {updateMutation.isPending ? t("common.saving") : t("common.save")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
