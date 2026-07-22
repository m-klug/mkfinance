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
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { MoneyField, SelectField, TextField } from "@/components/form/fields";
import { translateApiError } from "@/lib/errors";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import {
  getListAccountsQueryKey,
  useListAccounts,
} from "@/lib/api/generated/accounts/accounts";
import { useListCategories } from "@/lib/api/generated/categories/categories";
import {
  getListTransactionsQueryKey,
  useCreateTransaction,
} from "@/lib/api/generated/transactions/transactions";
import { useCreateTransfer } from "@/lib/api/generated/transfers/transfers";
import {
  CategoryType,
  ContributionPillar,
  TransactionRequestType,
  TransactionStatus,
  type Account,
  type TransactionRequest,
  type TransferRequest,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import {
  contributionPillarOptions,
  isContributionAccount,
} from "@/features/accounts/accountOptions";
import {
  activeAccounts,
  categoryOptions,
  todayIso,
} from "@/features/transactions/transactionUtils";

// The four things a user can register from anywhere in the app. A contribution
// (aporte, R9) is a transfer into a savings/investment account, so it shares
// the transfer payload — it is a separate tab only because it is a distinct
// intention, and it pre-filters the destination to eligible accounts.
const QUICK_MODES = ["EXPENSE", "INCOME", "TRANSFER", "CONTRIBUTION"] as const;
type QuickMode = (typeof QUICK_MODES)[number];

interface QuickTransactionDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

interface FormErrors {
  amount?: string;
  description?: string;
  category?: string;
  accounts?: string;
}

export function QuickTransactionDialog({ open, onOpenChange }: QuickTransactionDialogProps) {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const workspaceId = useCurrentWorkspaceId();

  const [mode, setMode] = useState<QuickMode>("EXPENSE");
  const [amountCents, setAmountCents] = useState(0);
  const [description, setDescription] = useState("");
  const [date, setDate] = useState(todayIso);
  const [accountId, setAccountId] = useState("");
  const [toAccountId, setToAccountId] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [pending, setPending] = useState(false);
  // Null means "follow the destination account default" (R9).
  const [pillarOverride, setPillarOverride] = useState<ContributionPillar | null>(null);
  const [errors, setErrors] = useState<FormErrors>({});

  const enabled = Boolean(workspaceId) && open;
  const { data: accountsData } = useListAccounts(
    workspaceId ?? "",
    undefined,
    { query: { enabled } },
  );
  const { data: categoriesData } = useListCategories(
    workspaceId ?? "",
    undefined,
    { query: { enabled } },
  );

  const accounts = useMemo(() => activeAccounts(accountsData ?? []), [accountsData]);
  const contributionAccounts = useMemo(
    () => accounts.filter(isContributionAccount),
    [accounts],
  );

  const categories = useMemo(() => {
    const wanted = mode === "INCOME" ? CategoryType.INCOME : CategoryType.EXPENSE;
    return (categoriesData ?? []).filter(
      (category) => category.type === wanted && !category.system,
    );
  }, [categoriesData, mode]);

  const isTransferLike = mode === "TRANSFER" || mode === "CONTRIBUTION";
  const destinationAccounts = mode === "CONTRIBUTION" ? contributionAccounts : accounts;

  // Reset to a clean, ready-to-submit state every time the modal opens: the
  // whole point of the quick entry is to reach "save" in <= 5 interactions,
  // which only works if account and date already carry sensible defaults.
  useEffect(() => {
    if (!open) return;
    setAmountCents(0);
    setDescription("");
    setDate(todayIso());
    setCategoryId("");
    setPillarOverride(null);
    setPending(false);
    setErrors({});
  }, [open]);

  useEffect(() => {
    if (!open || accounts.length === 0) return;
    setAccountId((current) =>
      accounts.some((account) => account.id === current) ? current : accounts[0].id,
    );
  }, [open, accounts]);

  useEffect(() => {
    if (!open) return;
    const candidates = mode === "CONTRIBUTION" ? contributionAccounts : accounts;
    // Never preselect the origin as destination: with a single eligible account
    // the field stays empty and validation asks the user to pick one.
    const fallback = candidates.find((account) => account.id !== accountId);
    setToAccountId((current) =>
      candidates.some((account) => account.id === current) && current !== accountId
        ? current
        : (fallback?.id ?? ""),
    );
  }, [open, mode, accounts, contributionAccounts, accountId]);

  const destination: Account | undefined = accounts.find((account) => account.id === toAccountId);
  // R9: a transfer into an INVESTMENT/SAVINGS account consumes a pillar. The
  // selector shows up automatically and comes pre-filled from the account.
  const isContribution = Boolean(destination && isContributionAccount(destination));
  const contributionPillar: ContributionPillar =
    pillarOverride ??
    destination?.defaultContributionPillar ??
    ContributionPillar.FINANCIAL_FREEDOM;

  const createTransaction = useCreateTransaction();
  const createTransfer = useCreateTransfer();
  const isSubmitting = createTransaction.isPending || createTransfer.isPending;

  function validate(): FormErrors {
    const next: FormErrors = {};
    if (amountCents <= 0) next.amount = t("validation.amountPositive");
    if (!description.trim()) next.description = t("validation.required");
    if (!isTransferLike && !categoryId) next.category = t("validation.required");
    if (isTransferLike) {
      if (!accountId || !toAccountId) next.accounts = t("validation.transferAccountsRequired");
      else if (accountId === toAccountId) next.accounts = t("validation.transferAccountsDistinct");
    } else if (!accountId) {
      next.accounts = t("validation.required");
    }
    return next;
  }

  function invalidateAfterWrite() {
    if (!workspaceId) return;
    void queryClient.invalidateQueries({ queryKey: getListTransactionsQueryKey(workspaceId) });
    // Balances move with every write (R5).
    void queryClient.invalidateQueries({ queryKey: getListAccountsQueryKey(workspaceId) });
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!workspaceId) return;
    const nextErrors = validate();
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length > 0) return;

    const onSuccess = () => {
      invalidateAfterWrite();
      toast.success(t("quickEntry.created"));
      onOpenChange(false);
    };
    const onError = (error: unknown) => {
      toast.error(translateApiError(t, error));
    };

    if (isTransferLike) {
      const data: TransferRequest = {
        fromAccountId: accountId,
        toAccountId,
        amountCents,
        date,
        description: description.trim(),
        contributionPillar: isContribution ? contributionPillar : null,
      };
      createTransfer.mutate({ workspaceId, data }, { onSuccess, onError });
      return;
    }

    const data: TransactionRequest = {
      accountId,
      categoryId,
      type: mode === "INCOME" ? TransactionRequestType.INCOME : TransactionRequestType.EXPENSE,
      amountCents,
      date,
      description: description.trim(),
      status: pending ? TransactionStatus.PENDING : TransactionStatus.SETTLED,
    };
    createTransaction.mutate({ workspaceId, data }, { onSuccess, onError });
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("quickEntry.title")}</DialogTitle>
          <DialogDescription>{t("quickEntry.subtitle")}</DialogDescription>
        </DialogHeader>

        <Tabs value={mode} onValueChange={(value) => setMode(value as QuickMode)}>
          <TabsList className="w-full">
            {QUICK_MODES.map((quickMode) => (
              <TabsTrigger key={quickMode} value={quickMode}>
                {t(`quickEntry.mode.${quickMode}`)}
              </TabsTrigger>
            ))}
          </TabsList>
        </Tabs>

        <form className="flex flex-col gap-4" onSubmit={handleSubmit} noValidate>
          <MoneyField
            id="quick-amount"
            label={t("transactions.amount")}
            value={amountCents}
            onValueChange={setAmountCents}
            error={errors.amount}
          />
          <TextField
            id="quick-description"
            label={t("transactions.description")}
            maxLength={255}
            autoComplete="off"
            value={description}
            error={errors.description}
            onChange={(event) => setDescription(event.target.value)}
          />

          {!isTransferLike && (
            <SelectField
              id="quick-category"
              label={t("transactions.category")}
              placeholder={t("transactions.categoryPlaceholder")}
              options={categoryOptions(categories)}
              value={categoryId}
              error={errors.category}
              onChange={(event) => setCategoryId(event.target.value)}
            />
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <SelectField
              id="quick-account"
              label={isTransferLike ? t("transactions.fromAccount") : t("transactions.account")}
              options={accounts.map((account) => ({ value: account.id, label: account.name }))}
              value={accountId}
              error={errors.accounts}
              onChange={(event) => setAccountId(event.target.value)}
            />
            {isTransferLike && (
              <SelectField
                id="quick-to-account"
                label={t("transactions.toAccount")}
                placeholder={t("transactions.toAccountPlaceholder")}
                options={destinationAccounts.map((account) => ({
                  value: account.id,
                  label: account.name,
                }))}
                value={toAccountId}
                onChange={(event) => {
                  setToAccountId(event.target.value);
                  // A new destination re-applies that account's default pillar.
                  setPillarOverride(null);
                }}
              />
            )}
          </div>

          {isTransferLike && isContribution && (
            <SelectField
              id="quick-contribution-pillar"
              label={t("transactions.contributionPillar")}
              hint={t("transactions.contributionPillarHint")}
              options={contributionPillarOptions(t)}
              value={contributionPillar}
              onChange={(event) =>
                setPillarOverride(event.target.value as ContributionPillar)
              }
            />
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <TextField
              id="quick-date"
              label={t("transactions.date")}
              type="date"
              value={date}
              onChange={(event) => setDate(event.target.value)}
            />
            {!isTransferLike && (
              <label className="flex items-end gap-2 pb-2 text-sm text-muted-foreground">
                <input
                  type="checkbox"
                  className="size-4"
                  checked={pending}
                  onChange={(event) => setPending(event.target.checked)}
                />
                {t("transactions.schedulePending")}
              </label>
            )}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              {t("common.cancel")}
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? t("common.saving") : t("common.save")}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
