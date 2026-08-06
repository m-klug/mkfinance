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
import { Label } from "@/components/ui/label";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { MoneyField, SelectField, TextField } from "@/components/form/fields";
import { formatCents } from "@/lib/format";
import { translateApiError } from "@/lib/errors";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import {
  getListAccountsQueryKey,
  useListAccounts,
} from "@/lib/api/generated/accounts/accounts";
import { useListCategories } from "@/lib/api/generated/categories/categories";
import {
  getListCardsQueryKey,
  useCreateCardPurchase,
  useListCards,
} from "@/lib/api/generated/cards/cards";
import { getListStatementsQueryKey } from "@/lib/api/generated/statements/statements";
import {
  getListTransactionsQueryKey,
  useCreateTransaction,
} from "@/lib/api/generated/transactions/transactions";
import { useCreateTransfer } from "@/lib/api/generated/transfers/transfers";
import { useListVaults } from "@/lib/api/generated/vaults/vaults";
import {
  CategoryType,
  ContributionPillar,
  TransactionRequestType,
  TransactionStatus,
  type Account,
  type CardPurchaseRequest,
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
import { TagsInput } from "@/features/transactions/TagsInput";
import { INSTALLMENT_COUNTS, splitInstallments } from "@/features/cards/cardOptions";

// The things a user can register from anywhere in the app. A contribution
// (aporte, R9) is a transfer into a savings/investment account, so it shares
// the transfer payload — it is a separate tab only because it is a distinct
// intention, and it pre-filters the destination to eligible accounts. A card
// purchase (compra no cartão) posts to a card and generates its installments.
const QUICK_MODES = ["EXPENSE", "INCOME", "TRANSFER", "CONTRIBUTION", "CARD"] as const;
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
  card?: string;
  fromVault?: string;
  toVault?: string;
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
  const [cardId, setCardId] = useState("");
  const [installmentCount, setInstallmentCount] = useState(1);
  const [tags, setTags] = useState<string[]>([]);
  const [pending, setPending] = useState(false);
  // Null means "follow the destination account default" (R9).
  const [pillarOverride, setPillarOverride] = useState<ContributionPillar | null>(null);
  // Which vault each leg names (§4.4) — required, not optional, once the
  // corresponding account has any active vault; a brand-new transfer/aporte
  // has no "leave it as is" escape hatch the way editing one does.
  const [fromVaultId, setFromVaultId] = useState("");
  const [toVaultId, setToVaultId] = useState("");
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
  const { data: cardsData } = useListCards(workspaceId ?? "", undefined, {
    query: { enabled },
  });

  const accounts = useMemo(() => activeAccounts(accountsData ?? []), [accountsData]);
  const contributionAccounts = useMemo(
    () => accounts.filter(isContributionAccount),
    [accounts],
  );
  const cards = useMemo(
    () => (cardsData ?? []).filter((card) => !card.archived),
    [cardsData],
  );

  const categories = useMemo(() => {
    const wanted = mode === "INCOME" ? CategoryType.INCOME : CategoryType.EXPENSE;
    return (categoriesData ?? []).filter(
      (category) => category.type === wanted && !category.system,
    );
  }, [categoriesData, mode]);

  const isTransferLike = mode === "TRANSFER" || mode === "CONTRIBUTION";
  const isCardMode = mode === "CARD";
  // Tags live only on plain income/expense entries (BE-8 scope: transfers and
  // card purchases carry no tags field in the contract).
  const supportsTags = mode === "EXPENSE" || mode === "INCOME";
  const destinationAccounts = mode === "CONTRIBUTION" ? contributionAccounts : accounts;

  // Preview mirrors the backend split: the cent remainder rides the last
  // installment (R1), e.g. 100,00 in 3× → 33,33 + 33,33 + 33,34.
  const installmentPreview = useMemo(
    () => (amountCents > 0 ? splitInstallments(amountCents, installmentCount) : []),
    [amountCents, installmentCount],
  );

  // Reset to a clean, ready-to-submit state every time the modal opens: the
  // whole point of the quick entry is to reach "save" in <= 5 interactions,
  // which only works if account and date already carry sensible defaults.
  useEffect(() => {
    if (!open) return;
    setAmountCents(0);
    setDescription("");
    setDate(todayIso());
    setCategoryId("");
    setInstallmentCount(1);
    setTags([]);
    setPillarOverride(null);
    setFromVaultId("");
    setToVaultId("");
    setPending(false);
    setErrors({});
  }, [open]);

  // A vault belongs to the account it was created under — moving either leg
  // to a different account invalidates whatever was selected before.
  useEffect(() => {
    setFromVaultId("");
  }, [accountId]);

  useEffect(() => {
    setToVaultId("");
  }, [toAccountId]);

  useEffect(() => {
    if (!open || accounts.length === 0) return;
    setAccountId((current) =>
      accounts.some((account) => account.id === current) ? current : accounts[0].id,
    );
  }, [open, accounts]);

  useEffect(() => {
    if (!open || cards.length === 0) return;
    setCardId((current) =>
      cards.some((card) => card.id === current) ? current : cards[0].id,
    );
  }, [open, cards]);

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

  const origin: Account | undefined = accounts.find((account) => account.id === accountId);
  const destination: Account | undefined = accounts.find((account) => account.id === toAccountId);
  // R9: a transfer into an INVESTMENT/SAVINGS account consumes a pillar. The
  // selector shows up automatically and comes pre-filled from the account.
  const isContribution = Boolean(destination && isContributionAccount(destination));
  const contributionPillar: ContributionPillar =
    pillarOverride ??
    destination?.defaultContributionPillar ??
    ContributionPillar.FINANCIAL_FREEDOM;

  // §4.4: once an account has any active vault, every leg touching it must
  // name one — the origin for a withdrawal, the destination for a
  // contribution, and with it the pillar the vault (not the account) decides.
  const originHasVault = Boolean(origin?.hasVaults);
  const destinationHasVault = Boolean(destination?.hasVaults);
  const { data: originVaultsData } = useListVaults(workspaceId ?? "", accountId, undefined, {
    query: { enabled: enabled && isTransferLike && originHasVault },
  });
  const { data: destinationVaultsData } = useListVaults(workspaceId ?? "", toAccountId, undefined, {
    query: { enabled: enabled && isTransferLike && destinationHasVault },
  });
  const originVaults = useMemo(
    () => (originVaultsData?.vaults ?? []).filter((vault) => !vault.archived),
    [originVaultsData],
  );
  const destinationVaults = useMemo(
    () => (destinationVaultsData?.vaults ?? []).filter((vault) => !vault.archived),
    [destinationVaultsData],
  );
  const selectedToVault = destinationVaults.find((vault) => vault.id === toVaultId);

  const createTransaction = useCreateTransaction();
  const createTransfer = useCreateTransfer();
  const createCardPurchase = useCreateCardPurchase();
  const isSubmitting =
    createTransaction.isPending || createTransfer.isPending || createCardPurchase.isPending;

  function validate(): FormErrors {
    const next: FormErrors = {};
    if (amountCents <= 0) next.amount = t("validation.amountPositive");
    if (!description.trim()) next.description = t("validation.required");
    if (!isTransferLike && !categoryId) next.category = t("validation.required");
    if (isCardMode) {
      if (!cardId) next.card = t("validation.required");
    } else if (isTransferLike) {
      if (!accountId || !toAccountId) next.accounts = t("validation.transferAccountsRequired");
      else if (accountId === toAccountId) next.accounts = t("validation.transferAccountsDistinct");
      if (originHasVault && !fromVaultId) next.fromVault = t("validation.vaultRequired");
      if (destinationHasVault && !toVaultId) next.toVault = t("validation.vaultRequired");
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

    if (isCardMode) {
      const data: CardPurchaseRequest = {
        categoryId,
        description: description.trim(),
        totalAmountCents: amountCents,
        purchaseDate: date,
        installmentCount,
      };
      createCardPurchase.mutate(
        { workspaceId, cardId, data },
        {
          onSuccess: () => {
            // A purchase moves the available limit and the card's statements,
            // but creates no account transaction until the bill is paid.
            void queryClient.invalidateQueries({ queryKey: getListCardsQueryKey(workspaceId) });
            void queryClient.invalidateQueries({
              queryKey: getListStatementsQueryKey(workspaceId, cardId),
            });
            toast.success(t("quickEntry.cardPurchaseCreated"));
            onOpenChange(false);
          },
          onError,
        },
      );
      return;
    }

    if (isTransferLike) {
      const data: TransferRequest = {
        fromAccountId: accountId,
        toAccountId,
        amountCents,
        date,
        description: description.trim(),
        // Once the destination has a vault, the vault's own pillar decides
        // (§4.4) — sending one here would only risk VAULT_PILLAR_CONFLICT.
        contributionPillar: isContribution && !destinationHasVault ? contributionPillar : null,
        fromVaultId: originHasVault ? fromVaultId || null : null,
        toVaultId: destinationHasVault ? toVaultId || null : null,
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
      // Optional and off the critical path (≤5-interaction target) — an empty
      // list here is equivalent to omitting the field on a brand-new entry.
      tags,
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

          {!isCardMode && (
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
          )}

          {isTransferLike && (originHasVault || destinationHasVault) && (
            <div className="grid gap-4 sm:grid-cols-2">
              {originHasVault && (
                <SelectField
                  id="quick-from-vault"
                  label={t("transactions.fromVault")}
                  placeholder={t("transactions.vaultPlaceholder")}
                  options={originVaults.map((vault) => ({ value: vault.id, label: vault.name }))}
                  value={fromVaultId}
                  error={errors.fromVault}
                  onChange={(event) => setFromVaultId(event.target.value)}
                />
              )}
              {destinationHasVault && (
                <SelectField
                  id="quick-to-vault"
                  label={t("transactions.toVault")}
                  placeholder={t("transactions.vaultPlaceholder")}
                  options={destinationVaults.map((vault) => ({ value: vault.id, label: vault.name }))}
                  value={toVaultId}
                  error={errors.toVault}
                  onChange={(event) => setToVaultId(event.target.value)}
                />
              )}
            </div>
          )}

          {isCardMode && (
            <>
              <div className="grid gap-4 sm:grid-cols-2">
                <SelectField
                  id="quick-card"
                  label={t("quickEntry.card")}
                  placeholder={t("quickEntry.cardPlaceholder")}
                  options={cards.map((card) => ({ value: card.id, label: card.name }))}
                  value={cardId}
                  error={errors.card}
                  onChange={(event) => setCardId(event.target.value)}
                />
                <SelectField
                  id="quick-installments"
                  label={t("quickEntry.installments")}
                  options={INSTALLMENT_COUNTS.map((count) => ({
                    value: String(count),
                    label: String(count),
                  }))}
                  value={String(installmentCount)}
                  onChange={(event) => setInstallmentCount(Number(event.target.value))}
                />
              </div>
              {installmentPreview.length > 0 && (
                <div className="rounded-md border p-3 text-sm">
                  <p className="mb-1 font-medium">{t("quickEntry.installmentPreview")}</p>
                  <ul className="flex flex-col gap-0.5">
                    {installmentPreview.map((cents, index) => (
                      <li
                        key={index}
                        className="flex justify-between tabular-nums text-muted-foreground"
                      >
                        <span>
                          {t("statement.installmentOf", {
                            number: index + 1,
                            count: installmentCount,
                          })}
                        </span>
                        <span>{formatCents(cents)}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </>
          )}

          {supportsTags && (
            <TagsInput
              id="quick-tags"
              label={t("transactions.tags")}
              workspaceId={workspaceId}
              value={tags}
              onChange={setTags}
            />
          )}

          {isTransferLike && isContribution && (
            destinationHasVault ? (
              // §4.4: with a vault, the pillar is the vault's to decide, not
              // an editable field — it stops being a choice the moment a
              // vault is picked above.
              <div className="flex flex-col gap-2">
                <Label htmlFor="quick-contribution-pillar-derived">
                  {t("transactions.contributionPillar")}
                </Label>
                <p id="quick-contribution-pillar-derived" className="text-sm">
                  {selectedToVault
                    ? t(`pillar.${selectedToVault.contributionPillar}`)
                    : t("transactions.contributionPillarPendingVault")}
                </p>
                <p className="text-xs text-muted-foreground">
                  {t("transactions.contributionPillarFromVaultHint")}
                </p>
              </div>
            ) : (
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
            )
          )}

          <div className="grid gap-4 sm:grid-cols-2">
            <TextField
              id="quick-date"
              label={isCardMode ? t("quickEntry.purchaseDate") : t("transactions.date")}
              type="date"
              value={date}
              onChange={(event) => setDate(event.target.value)}
            />
            {!isTransferLike && !isCardMode && (
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
