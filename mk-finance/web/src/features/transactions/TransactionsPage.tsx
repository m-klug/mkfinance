import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Check, Pencil, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { SelectField, TextField } from "@/components/form/fields";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { cn } from "@/lib/utils";
import { formatCents, formatDate } from "@/lib/format";
import { translateApiError } from "@/lib/errors";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import {
  getListAccountsQueryKey,
  useListAccounts,
} from "@/lib/api/generated/accounts/accounts";
import { useListCategories } from "@/lib/api/generated/categories/categories";
import {
  getListTransactionsQueryKey,
  useDeleteTransaction,
  useListTransactions,
  useSettleTransaction,
} from "@/lib/api/generated/transactions/transactions";
import {
  TransactionStatus,
  TransactionType,
  type ListTransactionsParams,
  type Transaction,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import { QuickTransactionDialog } from "@/features/transactions/QuickTransactionDialog";
import { TransactionFormDialog } from "@/features/transactions/TransactionFormDialog";
import {
  categoryOptions,
  firstDayOfCurrentMonthIso,
  lookupName,
  signedAmountCents,
  todayIso,
  transactionTypeOptions,
} from "@/features/transactions/transactionUtils";

const PAGE_SIZE = 20;

interface Filters {
  from: string;
  to: string;
  accountId: string;
  categoryId: string;
  type: string;
  status: string;
  search: string;
}

export default function TransactionsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const workspaceId = useCurrentWorkspaceId();

  // Defaults to the running month — the view a user wants 95% of the time.
  const [filters, setFilters] = useState<Filters>(() => ({
    from: firstDayOfCurrentMonthIso(),
    to: todayIso(),
    accountId: "",
    categoryId: "",
    type: "",
    status: "",
    search: "",
  }));
  const [page, setPage] = useState(0);
  const [quickOpen, setQuickOpen] = useState(false);
  const [editing, setEditing] = useState<Transaction | undefined>();
  const [deleting, setDeleting] = useState<Transaction | undefined>();

  const enabled = Boolean(workspaceId);
  const { data: accountsData } = useListAccounts(workspaceId ?? "", undefined, {
    query: { enabled },
  });
  const { data: categoriesData } = useListCategories(workspaceId ?? "", undefined, {
    query: { enabled },
  });

  const accounts = useMemo(() => accountsData ?? [], [accountsData]);
  const categories = useMemo(() => categoriesData ?? [], [categoriesData]);

  const params: ListTransactionsParams = {
    from: filters.from || undefined,
    to: filters.to || undefined,
    accountId: filters.accountId || undefined,
    categoryId: filters.categoryId || undefined,
    type: (filters.type as TransactionType) || undefined,
    status: (filters.status as TransactionStatus) || undefined,
    search: filters.search.trim() || undefined,
    page,
    size: PAGE_SIZE,
  };

  const { data: pageData, isPending } = useListTransactions(workspaceId ?? "", params, {
    query: { enabled },
  });

  const settleMutation = useSettleTransaction();
  const deleteMutation = useDeleteTransaction();

  function updateFilter<K extends keyof Filters>(key: K, value: Filters[K]) {
    setFilters((current) => ({ ...current, [key]: value }));
    setPage(0);
  }

  function invalidateAfterWrite() {
    if (!workspaceId) return;
    void queryClient.invalidateQueries({ queryKey: getListTransactionsQueryKey(workspaceId) });
    void queryClient.invalidateQueries({ queryKey: getListAccountsQueryKey(workspaceId) });
  }

  // R5: settling a pending entry is a single click, no confirmation step.
  function handleSettle(transaction: Transaction) {
    if (!workspaceId) return;
    settleMutation.mutate(
      { workspaceId, transactionId: transaction.id },
      {
        onSuccess: () => {
          invalidateAfterWrite();
          toast.success(t("transactions.settled"));
        },
        onError: (error) => {
          toast.error(translateApiError(t, error));
        },
      },
    );
  }

  function confirmDelete() {
    if (!workspaceId || !deleting) return;
    deleteMutation.mutate(
      { workspaceId, transactionId: deleting.id },
      {
        onSuccess: () => {
          invalidateAfterWrite();
          toast.success(t("transactions.deleted"));
          setDeleting(undefined);
        },
        onError: (error) => {
          toast.error(translateApiError(t, error));
        },
      },
    );
  }

  const transactions = pageData?.content ?? [];
  const totalElements = pageData?.totalElements ?? 0;
  const hasNextPage = (page + 1) * PAGE_SIZE < totalElements;

  return (
    <div className="flex flex-1 flex-col gap-6 p-4 md:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">{t("nav.transactions")}</h1>
        <Button onClick={() => setQuickOpen(true)}>{t("quickEntry.open")}</Button>
      </div>

      <Card>
        <CardContent className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <TextField
            id="filter-from"
            label={t("transactions.filterFrom")}
            type="date"
            value={filters.from}
            onChange={(event) => updateFilter("from", event.target.value)}
          />
          <TextField
            id="filter-to"
            label={t("transactions.filterTo")}
            type="date"
            value={filters.to}
            onChange={(event) => updateFilter("to", event.target.value)}
          />
          <SelectField
            id="filter-account"
            label={t("transactions.account")}
            placeholder={t("transactions.allAccounts")}
            options={accounts.map((account) => ({ value: account.id, label: account.name }))}
            value={filters.accountId}
            onChange={(event) => updateFilter("accountId", event.target.value)}
          />
          <SelectField
            id="filter-category"
            label={t("transactions.category")}
            placeholder={t("transactions.allCategories")}
            options={categoryOptions(categories)}
            value={filters.categoryId}
            onChange={(event) => updateFilter("categoryId", event.target.value)}
          />
          <SelectField
            id="filter-type"
            label={t("transactions.type")}
            placeholder={t("transactions.allTypes")}
            options={transactionTypeOptions(t)}
            value={filters.type}
            onChange={(event) => updateFilter("type", event.target.value)}
          />
          <SelectField
            id="filter-status"
            label={t("transactions.status")}
            placeholder={t("transactions.allStatuses")}
            options={[
              { value: TransactionStatus.SETTLED, label: t("transactionStatus.SETTLED") },
              { value: TransactionStatus.PENDING, label: t("transactionStatus.PENDING") },
            ]}
            value={filters.status}
            onChange={(event) => updateFilter("status", event.target.value)}
          />
          <TextField
            id="filter-search"
            label={t("transactions.search")}
            type="search"
            autoComplete="off"
            fieldClassName="lg:col-span-2"
            value={filters.search}
            onChange={(event) => updateFilter("search", event.target.value)}
          />
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          {isPending && <p className="text-muted-foreground">{t("common.loading")}</p>}
          {!isPending && transactions.length === 0 && (
            <p className="text-muted-foreground">{t("transactions.empty")}</p>
          )}
          {!isPending && transactions.length > 0 && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("transactions.date")}</TableHead>
                  <TableHead>{t("transactions.description")}</TableHead>
                  <TableHead>{t("transactions.account")}</TableHead>
                  <TableHead>{t("transactions.category")}</TableHead>
                  <TableHead className="text-right">{t("transactions.amount")}</TableHead>
                  <TableHead>{t("transactions.status")}</TableHead>
                  <TableHead className="text-right">{t("common.actions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {transactions.map((transaction) => (
                  <TableRow key={transaction.id}>
                    <TableCell>{formatDate(transaction.date)}</TableCell>
                    <TableCell className="font-medium">{transaction.description}</TableCell>
                    <TableCell>{lookupName(accounts, transaction.accountId) ?? "—"}</TableCell>
                    <TableCell>
                      {transaction.type === TransactionType.TRANSFER
                        ? t("transactionType.TRANSFER")
                        : (lookupName(categories, transaction.categoryId) ?? "—")}
                    </TableCell>
                    <TableCell
                      className={cn(
                        "text-right tabular-nums",
                        transaction.type === TransactionType.INCOME && "text-emerald-600",
                        transaction.type === TransactionType.EXPENSE && "text-destructive",
                      )}
                    >
                      {formatCents(signedAmountCents(transaction))}
                    </TableCell>
                    <TableCell>
                      <span
                        className={cn(
                          "rounded px-2 py-0.5 text-xs",
                          transaction.status === TransactionStatus.PENDING
                            ? "bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-100"
                            : "bg-muted text-muted-foreground",
                        )}
                      >
                        {t(`transactionStatus.${transaction.status}`)}
                      </span>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        {transaction.status === TransactionStatus.PENDING && (
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label={t("transactions.settleAction", {
                              description: transaction.description,
                            })}
                            disabled={settleMutation.isPending}
                            onClick={() => handleSettle(transaction)}
                          >
                            <Check className="size-4" />
                          </Button>
                        )}
                        {/* Transfer legs are mirrored pairs (R4); editing one
                            updates its sibling atomically through the dedicated
                            PATCH /transfers endpoint (§6.6). */}
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          aria-label={t("transactions.editAction", {
                            description: transaction.description,
                          })}
                          onClick={() => setEditing(transaction)}
                        >
                          <Pencil className="size-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          aria-label={t("transactions.deleteAction", {
                            description: transaction.description,
                          })}
                          onClick={() => setDeleting(transaction)}
                        >
                          <Trash2 className="size-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}

          {totalElements > PAGE_SIZE && (
            <div className="mt-4 flex items-center justify-between gap-2">
              <span className="text-sm text-muted-foreground">
                {t("transactions.total", { count: totalElements })}
              </span>
              <div className="flex gap-2">
                <Button
                  variant="outline"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                >
                  {t("common.previous")}
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  disabled={!hasNextPage}
                  onClick={() => setPage((current) => current + 1)}
                >
                  {t("common.next")}
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <QuickTransactionDialog open={quickOpen} onOpenChange={setQuickOpen} />

      {workspaceId && editing && (
        <TransactionFormDialog
          workspaceId={workspaceId}
          open={Boolean(editing)}
          onOpenChange={(open) => !open && setEditing(undefined)}
          transaction={editing}
          accounts={accounts}
          categories={categories}
        />
      )}

      <ConfirmDialog
        open={Boolean(deleting)}
        onOpenChange={(open) => !open && setDeleting(undefined)}
        title={t("transactions.deleteTitle")}
        description={t("transactions.deleteDescription", {
          description: deleting?.description ?? "",
        })}
        confirmLabel={t("common.delete")}
        destructive
        pending={deleteMutation.isPending}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
