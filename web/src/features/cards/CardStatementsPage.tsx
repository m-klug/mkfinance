import { useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, ChevronLeft, ChevronRight, Undo2 } from "lucide-react";

import { Badge } from "@/components/ui/badge";
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
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { formatCents, formatDate, formatMonth } from "@/lib/format";
import { translateApiError } from "@/lib/errors";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import {
  getListAccountsQueryKey,
  useListAccounts,
} from "@/lib/api/generated/accounts/accounts";
import { getListCardsQueryKey, useListCards } from "@/lib/api/generated/cards/cards";
import { useListCategories } from "@/lib/api/generated/categories/categories";
import { getListTransactionsQueryKey } from "@/lib/api/generated/transactions/transactions";
import {
  getGetStatementQueryKey,
  getListStatementsQueryKey,
  useGetStatement,
  useListStatements,
  useReverseStatementPayment,
} from "@/lib/api/generated/statements/statements";
import type { StatementStatus } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { lookupName } from "@/features/transactions/transactionUtils";
import {
  CURRENT_MONTH,
  addMonths,
  availableLimitCents,
  isDueSoon,
  isPayable,
  remainingCents,
} from "@/features/cards/cardOptions";
import { StatementPaymentDialog } from "@/features/cards/StatementPaymentDialog";

type BadgeVariant = "neutral" | "success" | "alert";

const STATUS_VARIANT: Record<StatementStatus, BadgeVariant> = {
  OPEN: "neutral",
  CLOSED: "alert",
  PARTIAL: "alert",
  PAID: "success",
};

export default function CardStatementsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const workspaceId = useCurrentWorkspaceId();
  const { cardId = "" } = useParams();

  const [month, setMonth] = useState(CURRENT_MONTH);
  const [payOpen, setPayOpen] = useState(false);
  const [reversing, setReversing] = useState<string | undefined>();

  const enabled = Boolean(workspaceId) && Boolean(cardId);

  // includeArchived so the header still resolves for an archived card whose
  // statements stay readable and payable (§6.2).
  const { data: cardsData } = useListCards(
    workspaceId ?? "",
    { includeArchived: true },
    { query: { enabled } },
  );
  const card = useMemo(
    () => (cardsData ?? []).find((item) => item.id === cardId),
    [cardsData, cardId],
  );

  const { data: accountsData } = useListAccounts(workspaceId ?? "", undefined, {
    query: { enabled },
  });
  const { data: categoriesData } = useListCategories(workspaceId ?? "", undefined, {
    query: { enabled },
  });
  const accounts = accountsData ?? [];
  const categories = categoriesData ?? [];

  // from=to=month asks the backend for exactly this reference month, creating
  // the statement lazily if the window has never been visited before.
  const { data: statements, isPending: listPending } = useListStatements(
    workspaceId ?? "",
    cardId,
    { from: month, to: month },
    { query: { enabled } },
  );
  const statement = statements?.[0];

  const { data: detail } = useGetStatement(workspaceId ?? "", statement?.id ?? "", {
    query: { enabled: enabled && Boolean(statement?.id) },
  });

  const remaining = statement ? remainingCents(statement) : 0;

  function invalidateAfterWrite() {
    if (!workspaceId) return;
    if (statement) {
      void queryClient.invalidateQueries({
        queryKey: getGetStatementQueryKey(workspaceId, statement.id),
      });
    }
    void queryClient.invalidateQueries({ queryKey: getListStatementsQueryKey(workspaceId, cardId) });
    // Available limit, account balances and the extrato all move with a payment.
    void queryClient.invalidateQueries({ queryKey: getListCardsQueryKey(workspaceId) });
    void queryClient.invalidateQueries({ queryKey: getListAccountsQueryKey(workspaceId) });
    void queryClient.invalidateQueries({ queryKey: getListTransactionsQueryKey(workspaceId) });
  }

  const reverseMutation = useReverseStatementPayment();

  function confirmReverse() {
    if (!workspaceId || !statement || !reversing) return;
    reverseMutation.mutate(
      { workspaceId, statementId: statement.id, paymentId: reversing },
      {
        onSuccess: () => {
          invalidateAfterWrite();
          toast.success(t("statement.paymentReversed"));
          setReversing(undefined);
        },
        onError: (error) => {
          toast.error(translateApiError(t, error));
        },
      },
    );
  }

  return (
    <div className="flex flex-1 flex-col gap-4 p-4 md:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Button
            asChild
            variant="ghost"
            size="icon-sm"
            className="rounded-pill text-ink-secondary hover:bg-neutral"
            aria-label={t("statement.backToCards")}
          >
            <Link to="/cards">
              <ArrowLeft className="size-4" />
            </Link>
          </Button>
          <div className="flex flex-col gap-0.5">
            <Link to="/cards" className="text-[11px] font-bold text-link hover:text-link-hover">
              {t("nav.cards")} › {card?.name ?? ""}
            </Link>
            <h1 className="text-2xl font-extrabold tracking-tight text-ink-primary">
              {formatMonth(month)}
            </h1>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {card && (
            <span className="hidden text-sm text-ink-secondary sm:inline">
              {t("cards.availableLimit")}:{" "}
              <span className="tabular-nums font-bold text-ink-primary">
                {formatCents(availableLimitCents(card))}
              </span>
            </span>
          )}
          <div className="flex items-center gap-0.5 rounded-pill bg-card p-1">
            <Button
              variant="ghost"
              size="icon-sm"
              className="rounded-pill text-ink-secondary hover:bg-neutral"
              aria-label={t("statement.previousMonth")}
              onClick={() => setMonth((current) => addMonths(current, -1))}
            >
              <ChevronLeft className="size-4" />
            </Button>
            <span className="min-w-24 px-2 text-center text-xs font-bold capitalize text-ink-primary">
              {formatMonth(month)}
            </span>
            <Button
              variant="ghost"
              size="icon-sm"
              className="rounded-pill text-ink-secondary hover:bg-neutral"
              aria-label={t("statement.nextMonth")}
              onClick={() => setMonth((current) => addMonths(current, 1))}
            >
              <ChevronRight className="size-4" />
            </Button>
          </div>
        </div>
      </div>

      {listPending && <p className="text-sm text-ink-tertiary">{t("common.loading")}</p>}

      {!listPending && !statement && (
        <Card className="rounded-card">
          <CardContent>
            <p className="text-sm text-ink-tertiary">{t("statement.empty")}</p>
          </CardContent>
        </Card>
      )}

      {statement && (
        <div className="grid gap-3.5 lg:grid-cols-[minmax(0,1fr)_330px] lg:items-start">
          <div className="flex flex-col gap-3.5">
            <div className="flex flex-col gap-3 rounded-card bg-hero p-5 text-hero-foreground sm:flex-row sm:items-end">
              <div className="flex flex-col gap-0.5">
                <span className="text-[11px] text-[#8d9298]">{t("statement.remaining")}</span>
                <span className="tabular-nums text-[30px] font-extrabold tracking-tight text-hero-foreground">
                  {formatCents(statement.totalCents)}
                </span>
                <span className="mt-1 flex flex-wrap items-center gap-2">
                  <Badge variant={STATUS_VARIANT[statement.status]}>
                    {t(`statementStatus.${statement.status}`)}
                  </Badge>
                  {isDueSoon(statement) && (
                    <Badge variant="alert">{t("statement.dueSoon")}</Badge>
                  )}
                  <span className="text-[11px] text-[#8d9298]">
                    {t("statement.dueDate")} {formatDate(statement.dueDate)} ·{" "}
                    {t("statement.closingDate")} {formatDate(statement.closingDate)}
                  </span>
                </span>
              </div>
              <div className="flex items-end gap-5 sm:ml-auto">
                <span className="flex flex-col">
                  <span className="text-[11px] text-[#8d9298]">{t("statement.paidLabel")}</span>
                  <span className="tabular-nums text-[16px] font-bold text-hero-foreground">
                    {formatCents(statement.paidCents)}
                  </span>
                </span>
                <span className="flex flex-col">
                  <span className="text-[11px] text-[#8d9298]">{t("statement.remaining")}</span>
                  <span className="tabular-nums text-[16px] font-bold text-mint">
                    {formatCents(remaining)}
                  </span>
                </span>
                {isPayable(statement) && (
                  <Button onClick={() => setPayOpen(true)}>{t("statement.pay")}</Button>
                )}
              </div>
            </div>

            <Card className="rounded-card p-2 sm:p-3">
              <CardContent className="p-0">
                {(detail?.items.length ?? 0) === 0 ? (
                  <p className="p-3 text-sm text-ink-tertiary">{t("statement.noItems")}</p>
                ) : (
                  <Table>
                    <TableHeader>
                      <TableRow className="border-divider-card hover:bg-transparent">
                        <TableHead className="text-[11px] font-bold text-ink-label">
                          {t("statement.itemDate")}
                        </TableHead>
                        <TableHead className="text-[11px] font-bold text-ink-label">
                          {t("statement.itemDescription")}
                        </TableHead>
                        <TableHead className="text-[11px] font-bold text-ink-label">
                          {t("statement.itemInstallment")}
                        </TableHead>
                        <TableHead className="text-[11px] font-bold text-ink-label">
                          {t("statement.itemCategory")}
                        </TableHead>
                        <TableHead className="text-right text-[11px] font-bold text-ink-label">
                          {t("statement.itemAmount")}
                        </TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {(detail?.items ?? []).map((item) => (
                        <TableRow key={item.installmentId} className="border-divider-row">
                          <TableCell className="tabular-nums text-ink-tertiary">
                            {formatDate(item.purchaseDate)}
                          </TableCell>
                          <TableCell className="font-bold text-ink-primary">
                            {item.description}
                          </TableCell>
                          <TableCell>
                            <Badge variant="neutral">
                              {t("statement.installmentOf", {
                                number: item.installmentNumber,
                                count: item.installmentCount,
                              })}
                            </Badge>
                          </TableCell>
                          <TableCell className="text-ink-secondary">
                            {lookupName(categories, item.categoryId) ?? "—"}
                          </TableCell>
                          <TableCell className="text-right tabular-nums font-extrabold text-ink-primary">
                            {formatCents(item.amountCents)}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
          </div>

          {(detail?.payments.length ?? 0) > 0 && (
            <Card className="rounded-card">
              <CardContent className="flex flex-col gap-3 p-0">
                <span className="text-[15px] font-extrabold tracking-tight text-ink-primary">
                  {t("statement.payments")}
                </span>
                <div className="flex flex-col gap-2">
                  {(detail?.payments ?? []).map((payment) => (
                    <div
                      key={payment.id}
                      className="flex flex-col gap-1.5 rounded-field bg-field p-3"
                    >
                      <div className="flex items-baseline gap-2.5">
                        <span className="tabular-nums text-[13px] font-extrabold text-ink-primary">
                          {formatCents(payment.amountCents)}
                        </span>
                        <span className="ml-auto tabular-nums text-[11px] text-ink-label">
                          {formatDate(payment.paymentDate)}
                        </span>
                      </div>
                      <div className="flex items-center gap-2.5">
                        <span className="text-[11px] text-ink-tertiary">
                          {lookupName(accounts, payment.accountId) ?? "—"}
                        </span>
                        <button
                          type="button"
                          className="ml-auto flex items-center gap-1 text-[11px] font-bold text-alert-ink"
                          aria-label={t("statement.reverseAction", {
                            amount: formatCents(payment.amountCents),
                          })}
                          onClick={() => setReversing(payment.id)}
                        >
                          <Undo2 className="size-3.5" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}

          {workspaceId && (
            <StatementPaymentDialog
              workspaceId={workspaceId}
              statementId={statement.id}
              remainingCents={remaining}
              accounts={accounts}
              defaultAccountId={card?.defaultPaymentAccountId}
              open={payOpen}
              onOpenChange={setPayOpen}
              onPaid={invalidateAfterWrite}
            />
          )}
        </div>
      )}

      <ConfirmDialog
        open={Boolean(reversing)}
        onOpenChange={(open) => !open && setReversing(undefined)}
        title={t("statement.reverseTitle")}
        description={t("statement.reverseDescription")}
        confirmLabel={t("statement.reverseConfirm")}
        destructive
        pending={reverseMutation.isPending}
        onConfirm={confirmReverse}
      />
    </div>
  );
}
