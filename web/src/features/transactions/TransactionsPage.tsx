import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueries, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  ArrowDownRight,
  ArrowUpRight,
  Check,
  ChevronLeft,
  ChevronRight,
  Equal,
  HandCoins,
  Pencil,
  Plus,
  SlidersHorizontal,
  Trash2,
  Upload,
  X,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import { ListRow } from "@/components/ui/list-row";
import { SegmentedControl } from "@/components/ui/segmented-control";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { SelectField, TextField } from "@/components/form/fields";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { cn } from "@/lib/utils";
import { formatDate } from "@/lib/format";
import { getListReimbursablePendingQueryKey } from "@/lib/api/generated/reimbursements/reimbursements";
import { useFormatCents } from "@/hooks/useFormatCents";
import { useIsMobile } from "@/hooks/useMediaQuery";
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
import { getListVaultsQueryOptions } from "@/lib/api/generated/vaults/vaults";
import {
  TransactionStatus,
  TransactionType,
  type Category,
  type ListTransactionsParams,
  type Transaction,
} from "@/lib/api/generated/mKFinanceAPI.schemas";
import { BankStatementImportDialog } from "@/features/transactions/BankStatementImportDialog";
import { QuickTransactionDialog } from "@/features/transactions/QuickTransactionDialog";
import { ReimbursementLinkDialog } from "@/features/transactions/ReimbursementLinkDialog";
import { TransactionFormDialog } from "@/features/transactions/TransactionFormDialog";
import { TagsInput } from "@/features/transactions/TagsInput";
import {
  categoryOptions,
  firstDayOfCurrentMonthIso,
  groupTransactionsByDate,
  lastDayOfCurrentMonthIso,
  lookupName,
  signedAmountCents,
  transactionTypeOptions,
  FORM_FIELD_CLASSNAME,
} from "@/features/transactions/transactionUtils";
import { PILLAR_COLORS } from "@/features/dashboard/dashboardFormat";
import { vaultCompositionQueryFilter } from "@/features/vaults/vaultOptions";

const PAGE_SIZE = 20;

interface Filters {
  from: string;
  to: string;
  accountId: string;
  categoryId: string;
  type: string;
  status: string;
  search: string;
  tags: string[];
}

function defaultFilters(): Filters {
  return {
    from: firstDayOfCurrentMonthIso(),
    to: lastDayOfCurrentMonthIso(),
    accountId: "",
    categoryId: "",
    type: "",
    status: "",
    search: "",
    tags: [],
  };
}

/** Category color marker (design_handoff_mk_finance/README.md — table "Categoria"
 * column dot): the category's own color when set, otherwise its pillar's fixed
 * color, falling back to a neutral gray for transfers/system categories. */
function categoryTone(category: Category | undefined): string {
  if (category?.color) return category.color;
  if (category?.pillar) return PILLAR_COLORS[category.pillar];
  return "var(--chart-gray-2, #CFD4D1)";
}

export default function TransactionsPage() {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const queryClient = useQueryClient();
  const workspaceId = useCurrentWorkspaceId();
  const isMobile = useIsMobile();

  // Defaults to the whole running month, not "up to today": a competency includes what is already
  // scheduled inside it — a recurrence occurrence, a bill entered with "agendar como pendente" —
  // and ending the window on today hid those rows even though they existed.
  const [filters, setFilters] = useState<Filters>(defaultFilters);
  const [page, setPage] = useState(0);
  const [quickOpen, setQuickOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [editing, setEditing] = useState<Transaction | undefined>();
  const [deleting, setDeleting] = useState<Transaction | undefined>();
  // ADR-0002: the received INCOME whose "link to expenses" dialog is open.
  const [linking, setLinking] = useState<Transaction | undefined>();
  const [filtersSheetOpen, setFiltersSheetOpen] = useState(false);

  const enabled = Boolean(workspaceId);
  const { data: accountsData } = useListAccounts(workspaceId ?? "", undefined, {
    query: { enabled },
  });
  const { data: categoriesData } = useListCategories(workspaceId ?? "", undefined, {
    query: { enabled },
  });

  const accounts = useMemo(() => accountsData ?? [], [accountsData]);
  const categories = useMemo(() => categoriesData ?? [], [categoriesData]);
  const categoryById = useMemo(
    () => new Map(categories.map((category) => [category.id, category])),
    [categories],
  );

  // §4.4 badge (Transaction.vaultId): one composition request per account
  // that actually has vaults, batched — most accounts have none, so this is
  // usually zero requests. Only the name is needed here, VaultsPage owns the
  // full composition view.
  const vaultedAccountIds = useMemo(
    () => accounts.filter((account) => account.hasVaults).map((account) => account.id),
    [accounts],
  );
  const vaultCompositionQueries = useQueries({
    queries: vaultedAccountIds.map((vaultedAccountId) => ({
      ...getListVaultsQueryOptions(workspaceId ?? "", vaultedAccountId),
      enabled,
    })),
  });
  const vaultNameById = useMemo(() => {
    const map = new Map<string, string>();
    for (const query of vaultCompositionQueries) {
      for (const vault of query.data?.vaults ?? []) {
        map.set(vault.id, vault.name);
      }
    }
    return map;
  }, [vaultCompositionQueries]);

  const params: ListTransactionsParams = {
    from: filters.from || undefined,
    to: filters.to || undefined,
    accountId: filters.accountId || undefined,
    categoryId: filters.categoryId || undefined,
    type: (filters.type as TransactionType) || undefined,
    status: (filters.status as TransactionStatus) || undefined,
    search: filters.search.trim() || undefined,
    tags: filters.tags.length > 0 ? filters.tags : undefined,
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

  function handleClearFilters() {
    setFilters(defaultFilters());
    setPage(0);
  }

  function invalidateAfterWrite() {
    if (!workspaceId) return;
    void queryClient.invalidateQueries({ queryKey: getListTransactionsQueryKey(workspaceId) });
    void queryClient.invalidateQueries({ queryKey: getListAccountsQueryKey(workspaceId) });
    void queryClient.invalidateQueries({ queryKey: getListReimbursablePendingQueryKey(workspaceId) });
    // A caixinha muda de saldo com o mesmo lançamento que muda a conta — sem isto uma
    // composição aberta segue mostrando o número de antes até o staleTime vencer.
    void queryClient.invalidateQueries(vaultCompositionQueryFilter(workspaceId));
  }

  // An imported extrato can pay card bills and move money between accounts, so besides the extrato
  // and the balances it changes what the cards and their statements show.
  function invalidateAfterImport() {
    if (!workspaceId) return;
    invalidateAfterWrite();
    void queryClient.invalidateQueries({
      predicate: ({ queryKey }) =>
        typeof queryKey[0] === "string" &&
        (queryKey[0].startsWith(`/workspaces/${workspaceId}/cards`) ||
          queryKey[0].startsWith(`/workspaces/${workspaceId}/statements`)),
    });
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

  // ADR-0002: only a settled INCOME can be linked to the expenses it paid back.
  function canLinkReimbursement(transaction: Transaction): boolean {
    return (
      transaction.type === TransactionType.INCOME && transaction.status === TransactionStatus.SETTLED
    );
  }

  /** Short text for the mobile subtitle; empty when the row has nothing to do with reimbursement. */
  function reimbursementNote(transaction: Transaction): string {
    if (transaction.type === TransactionType.EXPENSE && transaction.reimbursableCents) {
      const pendingCents = transaction.pendingReimbursementCents ?? 0;
      return ` · ${
        pendingCents > 0
          ? t("reimbursement.pendingBadge", { amount: formatCents(pendingCents) })
          : t("reimbursement.doneBadge", { amount: formatCents(transaction.effectiveAmountCents ?? 0) })
      }`;
    }
    if (transaction.type === TransactionType.INCOME && transaction.reimbursedCents) {
      return ` · ${t("reimbursement.incomeBadge", { amount: formatCents(transaction.reimbursedCents) })}`;
    }
    return "";
  }

  function reimbursementBadge(transaction: Transaction) {
    const note = reimbursementNote(transaction).replace(/^ · /, "");
    if (!note) return null;
    const waiting =
      transaction.type === TransactionType.EXPENSE && (transaction.pendingReimbursementCents ?? 0) > 0;
    return (
      <Badge variant={waiting ? "alert" : "success"} className="normal-case">
        {note}
      </Badge>
    );
  }

  const transactions = pageData?.content ?? [];
  const totalElements = pageData?.totalElements ?? 0;
  const hasNextPage = (page + 1) * PAGE_SIZE < totalElements;

  // KPI strip (design_handoff_mk_finance/README.md — screen 02): entradas/saídas/resultado
  // for the transactions currently loaded on screen. There is no dedicated aggregate
  // endpoint for the filtered extrato, so this reads off the same page already fetched
  // above instead of adding a new one — a page-scoped total, not a new business figure.
  const { incomeCents, expenseCents } = useMemo(() => {
    let income = 0;
    let expense = 0;
    for (const transaction of transactions) {
      const signed = signedAmountCents(transaction);
      if (signed >= 0) income += signed;
      else expense += Math.abs(signed);
    }
    return { incomeCents: income, expenseCents: expense };
  }, [transactions]);
  const netCents = incomeCents - expenseCents;

  // Mobile chips row (design_handoff_mk_finance mobile screen 02, "Traduções de
  // padrão desktop → mobile": filtros em barra → chips roláveis + sheet
  // "Filtros"): one removable chip per filter that differs from the default,
  // desktop's filter card stays untouched and is only hidden on mobile.
  const defaultFiltersValue = defaultFilters();
  const activeFilterChips: { key: string; label: string; onRemove: () => void }[] = [];
  if (filters.from !== defaultFiltersValue.from || filters.to !== defaultFiltersValue.to) {
    activeFilterChips.push({
      key: "period",
      label: `${formatDate(filters.from)} – ${formatDate(filters.to)}`,
      onRemove: () =>
        setFilters((current) => ({
          ...current,
          from: defaultFiltersValue.from,
          to: defaultFiltersValue.to,
        })),
    });
  }
  if (filters.accountId) {
    activeFilterChips.push({
      key: "account",
      label: lookupName(accounts, filters.accountId) ?? filters.accountId,
      onRemove: () => updateFilter("accountId", ""),
    });
  }
  if (filters.categoryId) {
    activeFilterChips.push({
      key: "category",
      label: lookupName(categories, filters.categoryId) ?? filters.categoryId,
      onRemove: () => updateFilter("categoryId", ""),
    });
  }
  if (filters.type) {
    activeFilterChips.push({
      key: "type",
      label: t(`transactionType.${filters.type}`),
      onRemove: () => updateFilter("type", ""),
    });
  }
  if (filters.status) {
    activeFilterChips.push({
      key: "status",
      label: t(`transactionStatus.${filters.status}`),
      onRemove: () => updateFilter("status", ""),
    });
  }
  if (filters.search.trim()) {
    activeFilterChips.push({
      key: "search",
      label: `“${filters.search.trim()}”`,
      onRemove: () => updateFilter("search", ""),
    });
  }
  if (filters.tags.length > 0) {
    activeFilterChips.push({
      key: "tags",
      label: filters.tags.join(", "),
      onRemove: () => updateFilter("tags", []),
    });
  }

  const dateGroups = groupTransactionsByDate(transactions, t);

  return (
    <div className="flex flex-1 flex-col gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-col gap-0.5">
          <h1 className="text-2xl font-extrabold tracking-tight text-ink-primary">
            {t("nav.transactions")}
          </h1>
          <p className="text-[13px] text-ink-secondary">
            {t("transactions.total", { count: totalElements })}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <Button variant="outline" onClick={() => setImportOpen(true)}>
            <Upload className="size-4" />
            {t("bankImport.action")}
          </Button>
          <Button onClick={() => setQuickOpen(true)}>
            <Plus className="size-4" />
            {t("quickEntry.open")}
          </Button>
        </div>
      </div>

      {isMobile ? (
        // Mobile KPI strip (design_handoff_mk_finance mobile screen 02): one
        // white card, three columns with thin dividers — not the dark
        // stacked hero used on the Dashboard, matching the mock exactly.
        <div className="flex items-stretch gap-3 rounded-card bg-card p-3.5">
          <div className="flex flex-1 flex-col gap-0.5">
            <span className="text-[10px] text-ink-secondary">{t("transactions.kpiIncome")}</span>
            <span className="text-base font-extrabold tabular-nums text-success-ink">
              {formatCents(incomeCents)}
            </span>
          </div>
          <div className="w-px bg-divider-row" />
          <div className="flex flex-1 flex-col gap-0.5">
            <span className="text-[10px] text-ink-secondary">{t("transactions.kpiExpense")}</span>
            <span className="text-base font-extrabold tabular-nums text-ink-primary">
              {formatCents(expenseCents)}
            </span>
          </div>
          <div className="w-px bg-divider-row" />
          <div className="flex flex-1 flex-col gap-0.5">
            <span className="text-[10px] text-ink-secondary">{t("transactions.kpiNet")}</span>
            <span className="text-base font-extrabold tabular-nums text-ink-primary">
              {netCents >= 0 ? "+ " : ""}
              {formatCents(netCents)}
            </span>
          </div>
        </div>
      ) : (
        <div className="grid gap-3.5 sm:grid-cols-3">
          <Card className="flex-row items-center gap-3.5 rounded-card p-4">
            <span className="flex size-9 shrink-0 items-center justify-center rounded-[12px] bg-success-bg text-success-ink">
              <ArrowDownRight className="size-4" />
            </span>
            <span className="flex flex-col gap-0.5">
              <span className="text-xs text-ink-secondary">{t("transactions.kpiIncome")}</span>
              <span className="tabular-nums text-xl font-extrabold tracking-tight text-ink-primary">
                {formatCents(incomeCents)}
              </span>
            </span>
          </Card>
          <Card className="flex-row items-center gap-3.5 rounded-card p-4">
            <span className="flex size-9 shrink-0 items-center justify-center rounded-[12px] bg-neutral text-ink-secondary">
              <ArrowUpRight className="size-4" />
            </span>
            <span className="flex flex-col gap-0.5">
              <span className="text-xs text-ink-secondary">{t("transactions.kpiExpense")}</span>
              <span className="tabular-nums text-xl font-extrabold tracking-tight text-ink-primary">
                {formatCents(expenseCents)}
              </span>
            </span>
          </Card>
          <Card className="flex-row items-center gap-3.5 rounded-card bg-hero p-4 text-hero-foreground">
            <span className="flex size-9 shrink-0 items-center justify-center rounded-[12px] bg-mint text-mint-ink">
              <Equal className="size-4" />
            </span>
            <span className="flex flex-col gap-0.5">
              <span className="text-xs text-hero-foreground/60">{t("transactions.kpiNet")}</span>
              <span className="tabular-nums text-xl font-extrabold tracking-tight text-hero-foreground">
                {netCents >= 0 ? "+ " : ""}
                {formatCents(netCents)}
              </span>
            </span>
          </Card>
        </div>
      )}

      {isMobile ? (
        <div className="flex items-center gap-2">
          <div className="flex flex-1 items-center gap-2 overflow-x-auto">
            {activeFilterChips.length === 0 ? (
              <span className="text-xs text-ink-tertiary">{t("transactions.allFilter")}</span>
            ) : (
              activeFilterChips.map((chip) => (
                <span
                  key={chip.key}
                  className="flex shrink-0 items-center gap-1.5 rounded-pill bg-card px-3 py-2 text-xs font-bold text-ink-secondary"
                >
                  <span className="max-w-[140px] truncate">{chip.label}</span>
                  <button
                    type="button"
                    aria-label={t("transactions.removeFilterChip", { label: chip.label })}
                    onClick={chip.onRemove}
                    className="flex size-4 shrink-0 items-center justify-center rounded-full text-ink-tertiary hover:text-ink-primary"
                  >
                    <X className="size-3" />
                  </button>
                </span>
              ))
            )}
          </div>
          <Button
            type="button"
            variant="secondary"
            size="icon"
            className="shrink-0 rounded-full"
            aria-label={t("transactions.filtersButton")}
            onClick={() => setFiltersSheetOpen(true)}
          >
            <SlidersHorizontal className="size-4" />
          </Button>
        </div>
      ) : (
      <Card className="rounded-card">
        <CardContent className="flex flex-col gap-3.5">
          {/* Date range, account and category share the top row — the four
              fields a user reaches for first. Type/status/tags sit below;
              tags gets a double column because it is a chip input, not a
              single-line control. Free-text search moves to its own
              full-width row instead of squeezing into the grid. */}
          <div className="grid gap-3.5 sm:grid-cols-2 lg:grid-cols-4">
            <TextField
              id="filter-from"
              label={t("transactions.filterFrom")}
              type="date"
              fieldClassName={FORM_FIELD_CLASSNAME}
              value={filters.from}
              onChange={(event) => updateFilter("from", event.target.value)}
            />
            <TextField
              id="filter-to"
              label={t("transactions.filterTo")}
              type="date"
              fieldClassName={FORM_FIELD_CLASSNAME}
              value={filters.to}
              onChange={(event) => updateFilter("to", event.target.value)}
            />
            <SelectField
              id="filter-account"
              label={t("transactions.account")}
              placeholder={t("transactions.allAccounts")}
              fieldClassName={FORM_FIELD_CLASSNAME}
              options={accounts.map((account) => ({ value: account.id, label: account.name }))}
              value={filters.accountId}
              onChange={(event) => updateFilter("accountId", event.target.value)}
            />
            <SelectField
              id="filter-category"
              label={t("transactions.category")}
              placeholder={t("transactions.allCategories")}
              fieldClassName={FORM_FIELD_CLASSNAME}
              options={categoryOptions(categories)}
              value={filters.categoryId}
              onChange={(event) => updateFilter("categoryId", event.target.value)}
            />
            <SelectField
              id="filter-type"
              label={t("transactions.type")}
              placeholder={t("transactions.allTypes")}
              fieldClassName={FORM_FIELD_CLASSNAME}
              options={transactionTypeOptions(t)}
              value={filters.type}
              onChange={(event) => updateFilter("type", event.target.value)}
            />
            <div className="flex flex-col gap-2">
              <span className="text-[11px] font-bold tracking-wide text-ink-label uppercase">
                {t("transactions.status")}
              </span>
              <SegmentedControl
                className="w-full"
                options={[
                  { value: "", label: t("transactions.allStatuses") },
                  { value: TransactionStatus.SETTLED, label: t("transactionStatus.SETTLED") },
                  { value: TransactionStatus.PENDING, label: t("transactionStatus.PENDING") },
                ]}
                value={filters.status}
                onChange={(value) => updateFilter("status", value)}
              />
            </div>
            <TagsInput
              id="filter-tags"
              label={t("transactions.tags")}
              fieldClassName="sm:col-span-2"
              workspaceId={workspaceId}
              value={filters.tags}
              onChange={(tags) => updateFilter("tags", tags)}
            />
          </div>
          <div className="flex items-center gap-3 border-t border-divider-card pt-3.5">
            <TextField
              id="filter-search"
              label={t("transactions.search")}
              type="search"
              autoComplete="off"
              fieldClassName={cn(FORM_FIELD_CLASSNAME, "flex-1")}
              value={filters.search}
              onChange={(event) => updateFilter("search", event.target.value)}
            />
            <button
              type="button"
              className="mt-6 shrink-0 text-xs font-bold text-link hover:text-link-hover"
              onClick={handleClearFilters}
            >
              {t("transactions.clearFilters")}
            </button>
          </div>
        </CardContent>
      </Card>
      )}

      <Card className="rounded-card">
        <CardContent>
          {isPending && <p className="text-ink-secondary">{t("common.loading")}</p>}
          {!isPending && transactions.length === 0 && (
            <p className="text-ink-secondary">{t("transactions.empty")}</p>
          )}
          {!isPending && transactions.length > 0 && isMobile && (
            <div className="flex flex-col gap-4">
              {dateGroups.map((group) => (
                <div key={group.dateIso} className="flex flex-col gap-2">
                  <span className="pl-1 text-[11px] font-extrabold tracking-wide text-ink-label">
                    {group.label}
                  </span>
                  <div className="flex flex-col rounded-card bg-field px-4">
                    {group.transactions.map((transaction, index) => {
                      const category = categoryById.get(transaction.categoryId ?? "");
                      const pending = transaction.status === TransactionStatus.PENDING;
                      const signed = signedAmountCents(transaction);
                      return (
                        <div key={transaction.id}>
                          {index > 0 && <div className="h-px bg-divider-row" />}
                          <ListRow
                            onClick={() => setEditing(transaction)}
                            aria-label={t("transactions.editAction", {
                              description: transaction.description,
                            })}
                            title={transaction.description}
                            subtitle={
                              <span className="flex min-w-0 items-center gap-1">
                                <span
                                  className="size-1.5 shrink-0 rounded-swatch"
                                  style={{ backgroundColor: categoryTone(category) }}
                                  aria-hidden
                                />
                                <span className="truncate">
                                  {lookupName(accounts, transaction.accountId) ?? "—"} ·{" "}
                                  {transaction.type === TransactionType.TRANSFER
                                    ? t("transactionType.TRANSFER")
                                    : (category?.name ?? "—")}
                                  {reimbursementNote(transaction)}
                                </span>
                              </span>
                            }
                            value={
                              <span
                                className={signed > 0 ? "text-success-ink" : "text-ink-primary"}
                              >
                                {formatCents(signed)}
                              </span>
                            }
                            trailing={
                              <>
                                {pending && (
                                  <Button
                                    size="sm"
                                    className="h-8 rounded-full px-3 text-[11px]"
                                    disabled={settleMutation.isPending}
                                    aria-label={t("transactions.settleAction", {
                                      description: transaction.description,
                                    })}
                                    onClick={() => handleSettle(transaction)}
                                  >
                                    {t("transactions.settleShort")}
                                  </Button>
                                )}
                                {canLinkReimbursement(transaction) && (
                                  <Button
                                    variant="secondary"
                                    size="icon-sm"
                                    className="rounded-full"
                                    aria-label={t("reimbursement.linkAction", {
                                      description: transaction.description,
                                    })}
                                    onClick={() => setLinking(transaction)}
                                  >
                                    <HandCoins className="size-3.5" />
                                  </Button>
                                )}
                                <Button
                                  variant="secondary"
                                  size="icon-sm"
                                  className="rounded-full"
                                  aria-label={t("transactions.deleteAction", {
                                    description: transaction.description,
                                  })}
                                  onClick={() => setDeleting(transaction)}
                                >
                                  <Trash2 className="size-3.5" />
                                </Button>
                              </>
                            }
                          />
                        </div>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          )}
          {!isPending && transactions.length > 0 && !isMobile && (
            <Table>
              <TableHeader>
                <TableRow className="border-divider-card hover:bg-transparent">
                  <TableHead className="text-[11px] font-bold text-ink-label uppercase">
                    {t("transactions.date")}
                  </TableHead>
                  <TableHead className="text-[11px] font-bold text-ink-label uppercase">
                    {t("transactions.description")}
                  </TableHead>
                  <TableHead className="text-[11px] font-bold text-ink-label uppercase">
                    {t("transactions.account")}
                  </TableHead>
                  <TableHead className="text-[11px] font-bold text-ink-label uppercase">
                    {t("transactions.category")}
                  </TableHead>
                  <TableHead className="text-right text-[11px] font-bold text-ink-label uppercase">
                    {t("transactions.amount")}
                  </TableHead>
                  <TableHead className="text-[11px] font-bold text-ink-label uppercase">
                    {t("transactions.status")}
                  </TableHead>
                  <TableHead className="text-right text-[11px] font-bold text-ink-label uppercase">
                    {t("common.actions")}
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {transactions.map((transaction) => {
                  const category = categoryById.get(transaction.categoryId ?? "");
                  return (
                    <TableRow key={transaction.id} className="border-divider-row hover:bg-neutral/40">
                      <TableCell className="text-xs tabular-nums text-ink-label">
                        {formatDate(transaction.date)}
                      </TableCell>
                      <TableCell className="font-bold text-ink-primary">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="text-[13px]">{transaction.description}</span>
                          {transaction.vaultId && vaultNameById.get(transaction.vaultId) && (
                            <Badge variant="success" className="normal-case">
                              <span aria-hidden>{t("transactions.vaultBadgePrefix")}</span>
                              {vaultNameById.get(transaction.vaultId)}
                            </Badge>
                          )}
                          {reimbursementBadge(transaction)}
                          {transaction.tags && transaction.tags.length > 0 && (
                            <span className="flex flex-wrap gap-1">
                              {transaction.tags.map((tag) => (
                                <span
                                  key={tag.id}
                                  className="text-[11px] font-semibold text-placeholder"
                                >
                                  <span aria-hidden>#</span>
                                  {tag.name}
                                </span>
                              ))}
                            </span>
                          )}
                        </div>
                      </TableCell>
                      <TableCell className="text-xs text-ink-secondary">
                        {lookupName(accounts, transaction.accountId) ?? "—"}
                      </TableCell>
                      <TableCell>
                        <span className="flex min-w-0 items-center gap-2 text-xs text-ink-secondary">
                          <span
                            className="size-2 shrink-0 rounded-[3px]"
                            style={{ backgroundColor: categoryTone(category) }}
                            aria-hidden
                          />
                          <span className="truncate">
                            {transaction.type === TransactionType.TRANSFER
                              ? t("transactionType.TRANSFER")
                              : (category?.name ?? "—")}
                          </span>
                        </span>
                      </TableCell>
                      <TableCell
                        className={cn(
                          "text-right text-sm font-extrabold tabular-nums",
                          signedAmountCents(transaction) > 0 ? "text-success-ink" : "text-ink-primary",
                        )}
                      >
                        {formatCents(signedAmountCents(transaction))}
                      </TableCell>
                      <TableCell>
                        <Badge
                          variant={
                            transaction.status === TransactionStatus.PENDING ? "alert" : "neutral"
                          }
                        >
                          {t(`transactionStatus.${transaction.status}`)}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1.5">
                          {transaction.status === TransactionStatus.PENDING && (
                            <Button
                              size="icon-sm"
                              className="rounded-full"
                              aria-label={t("transactions.settleAction", {
                                description: transaction.description,
                              })}
                              disabled={settleMutation.isPending}
                              onClick={() => handleSettle(transaction)}
                            >
                              <Check className="size-3.5" />
                            </Button>
                          )}
                          {canLinkReimbursement(transaction) && (
                            <Button
                              variant="secondary"
                              size="icon-sm"
                              className="rounded-full"
                              aria-label={t("reimbursement.linkAction", {
                                description: transaction.description,
                              })}
                              onClick={() => setLinking(transaction)}
                            >
                              <HandCoins className="size-3.5" />
                            </Button>
                          )}
                          {/* Transfer legs are mirrored pairs (R4); editing one
                              updates its sibling atomically through the dedicated
                              PATCH /transfers endpoint (§6.6). */}
                          <Button
                            variant="secondary"
                            size="icon-sm"
                            className="rounded-full"
                            aria-label={t("transactions.editAction", {
                              description: transaction.description,
                            })}
                            onClick={() => setEditing(transaction)}
                          >
                            <Pencil className="size-3.5" />
                          </Button>
                          <Button
                            variant="secondary"
                            size="icon-sm"
                            className="rounded-full"
                            aria-label={t("transactions.deleteAction", {
                              description: transaction.description,
                            })}
                            onClick={() => setDeleting(transaction)}
                          >
                            <Trash2 className="size-3.5" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}

          {totalElements > PAGE_SIZE && (
            <div className="mt-3.5 flex items-center justify-between gap-2 pt-1">
              <span className="text-xs text-ink-secondary">
                {t("transactions.showingCount", {
                  shown: transactions.length,
                  total: totalElements,
                })}
              </span>
              <div className="flex items-center gap-1.5">
                <Button
                  variant="secondary"
                  size="icon-sm"
                  className="rounded-full"
                  disabled={page === 0}
                  aria-label={t("common.previous")}
                  onClick={() => setPage((current) => Math.max(0, current - 1))}
                >
                  <ChevronLeft className="size-3.5" />
                </Button>
                <Button
                  variant="secondary"
                  size="icon-sm"
                  className="rounded-full"
                  disabled={!hasNextPage}
                  aria-label={t("common.next")}
                  onClick={() => setPage((current) => current + 1)}
                >
                  <ChevronRight className="size-3.5" />
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <QuickTransactionDialog open={quickOpen} onOpenChange={setQuickOpen} />

      {workspaceId && (
        <BankStatementImportDialog
          workspaceId={workspaceId}
          accounts={accounts}
          categories={categories}
          open={importOpen}
          onOpenChange={setImportOpen}
          onImported={invalidateAfterImport}
        />
      )}

      {/* Mobile filters bottom sheet (design_handoff_mk_finance mobile screen
          02): the same fields as the desktop filter card, reused as-is, just
          laid out in a single column inside the sheet. */}
      <Dialog open={filtersSheetOpen} onOpenChange={setFiltersSheetOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("transactions.filtersSheetTitle")}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-3.5">
            <TextField
              id="filter-from-mobile"
              label={t("transactions.filterFrom")}
              type="date"
              fieldClassName={FORM_FIELD_CLASSNAME}
              value={filters.from}
              onChange={(event) => updateFilter("from", event.target.value)}
            />
            <TextField
              id="filter-to-mobile"
              label={t("transactions.filterTo")}
              type="date"
              fieldClassName={FORM_FIELD_CLASSNAME}
              value={filters.to}
              onChange={(event) => updateFilter("to", event.target.value)}
            />
            <SelectField
              id="filter-account-mobile"
              label={t("transactions.account")}
              placeholder={t("transactions.allAccounts")}
              fieldClassName={FORM_FIELD_CLASSNAME}
              options={accounts.map((account) => ({ value: account.id, label: account.name }))}
              value={filters.accountId}
              onChange={(event) => updateFilter("accountId", event.target.value)}
            />
            <SelectField
              id="filter-category-mobile"
              label={t("transactions.category")}
              placeholder={t("transactions.allCategories")}
              fieldClassName={FORM_FIELD_CLASSNAME}
              options={categoryOptions(categories)}
              value={filters.categoryId}
              onChange={(event) => updateFilter("categoryId", event.target.value)}
            />
            <SelectField
              id="filter-type-mobile"
              label={t("transactions.type")}
              placeholder={t("transactions.allTypes")}
              fieldClassName={FORM_FIELD_CLASSNAME}
              options={transactionTypeOptions(t)}
              value={filters.type}
              onChange={(event) => updateFilter("type", event.target.value)}
            />
            <div className="flex flex-col gap-2">
              <span className="text-[11px] font-bold tracking-wide text-ink-label uppercase">
                {t("transactions.status")}
              </span>
              <SegmentedControl
                className="w-full"
                options={[
                  { value: "", label: t("transactions.allStatuses") },
                  { value: TransactionStatus.SETTLED, label: t("transactionStatus.SETTLED") },
                  { value: TransactionStatus.PENDING, label: t("transactionStatus.PENDING") },
                ]}
                value={filters.status}
                onChange={(value) => updateFilter("status", value)}
              />
            </div>
            <TagsInput
              id="filter-tags-mobile"
              label={t("transactions.tags")}
              workspaceId={workspaceId}
              value={filters.tags}
              onChange={(tags) => updateFilter("tags", tags)}
            />
            <TextField
              id="filter-search-mobile"
              label={t("transactions.search")}
              type="search"
              autoComplete="off"
              fieldClassName={FORM_FIELD_CLASSNAME}
              value={filters.search}
              onChange={(event) => updateFilter("search", event.target.value)}
            />
            <button
              type="button"
              className="self-start text-xs font-bold text-link hover:text-link-hover"
              onClick={handleClearFilters}
            >
              {t("transactions.clearFilters")}
            </button>
          </div>
          <DialogFooter>
            <Button type="button" onClick={() => setFiltersSheetOpen(false)}>
              {t("transactions.applyFilters")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

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

      {workspaceId && linking && (
        <ReimbursementLinkDialog
          workspaceId={workspaceId}
          open={Boolean(linking)}
          onOpenChange={(open) => !open && setLinking(undefined)}
          income={linking}
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
