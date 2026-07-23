import { useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ArrowLeft, ChevronLeft, ChevronRight, Undo2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { cn } from "@/lib/utils";
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

const STATUS_STYLES: Record<StatementStatus, string> = {
  OPEN: "bg-blue-100 text-blue-900 dark:bg-blue-900/40 dark:text-blue-100",
  CLOSED: "bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-100",
  PARTIAL: "bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-100",
  PAID: "bg-emerald-100 text-emerald-900 dark:bg-emerald-900/40 dark:text-emerald-100",
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
    <div className="flex flex-1 flex-col gap-6 p-4 md:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Button
            asChild
            variant="ghost"
            size="icon-sm"
            aria-label={t("statement.backToCards")}
          >
            <Link to="/cards">
              <ArrowLeft className="size-4" />
            </Link>
          </Button>
          <h1 className="text-2xl font-semibold">{card?.name ?? t("nav.cards")}</h1>
        </div>
        {card && (
          <span className="text-sm text-muted-foreground">
            {t("cards.availableLimit")}: {formatCents(availableLimitCents(card))}
          </span>
        )}
      </div>

      <div className="flex items-center justify-center gap-4">
        <Button
          variant="outline"
          size="icon-sm"
          aria-label={t("statement.previousMonth")}
          onClick={() => setMonth((current) => addMonths(current, -1))}
        >
          <ChevronLeft className="size-4" />
        </Button>
        <span className="min-w-40 text-center text-lg font-medium capitalize">
          {formatMonth(month)}
        </span>
        <Button
          variant="outline"
          size="icon-sm"
          aria-label={t("statement.nextMonth")}
          onClick={() => setMonth((current) => addMonths(current, 1))}
        >
          <ChevronRight className="size-4" />
        </Button>
      </div>

      {listPending && <p className="text-muted-foreground">{t("common.loading")}</p>}

      {!listPending && !statement && (
        <Card>
          <CardContent>
            <p className="text-muted-foreground">{t("statement.empty")}</p>
          </CardContent>
        </Card>
      )}

      {statement && (
        <>
          <Card>
            <CardHeader className="gap-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <CardTitle className="text-xl">{formatCents(statement.totalCents)}</CardTitle>
                <div className="flex items-center gap-2">
                  <span
                    className={cn(
                      "rounded px-2 py-0.5 text-xs font-medium",
                      STATUS_STYLES[statement.status],
                    )}
                  >
                    {t(`statementStatus.${statement.status}`)}
                  </span>
                  {isDueSoon(statement) && (
                    <span className="rounded bg-destructive/15 px-2 py-0.5 text-xs font-medium text-destructive">
                      {t("statement.dueSoon")}
                    </span>
                  )}
                </div>
              </div>
              <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm sm:grid-cols-4">
                <div>
                  <dt className="text-muted-foreground">{t("statement.closingDate")}</dt>
                  <dd className="tabular-nums">{formatDate(statement.closingDate)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">{t("statement.dueDate")}</dt>
                  <dd className="tabular-nums">{formatDate(statement.dueDate)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">{t("statement.paidLabel")}</dt>
                  <dd className="tabular-nums">{formatCents(statement.paidCents)}</dd>
                </div>
                <div>
                  <dt className="text-muted-foreground">{t("statement.remaining")}</dt>
                  <dd className="tabular-nums font-medium">{formatCents(remaining)}</dd>
                </div>
              </dl>
              {isPayable(statement) && (
                <div>
                  <Button onClick={() => setPayOpen(true)}>{t("statement.pay")}</Button>
                </div>
              )}
            </CardHeader>
          </Card>

          <Card>
            <CardContent>
              {(detail?.items.length ?? 0) === 0 ? (
                <p className="text-muted-foreground">{t("statement.noItems")}</p>
              ) : (
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t("statement.itemDate")}</TableHead>
                      <TableHead>{t("statement.itemDescription")}</TableHead>
                      <TableHead>{t("statement.itemInstallment")}</TableHead>
                      <TableHead>{t("statement.itemCategory")}</TableHead>
                      <TableHead className="text-right">{t("statement.itemAmount")}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {(detail?.items ?? []).map((item) => (
                      <TableRow key={item.installmentId}>
                        <TableCell className="tabular-nums">
                          {formatDate(item.purchaseDate)}
                        </TableCell>
                        <TableCell className="font-medium">{item.description}</TableCell>
                        <TableCell className="tabular-nums text-muted-foreground">
                          {t("statement.installmentOf", {
                            number: item.installmentNumber,
                            count: item.installmentCount,
                          })}
                        </TableCell>
                        <TableCell>{lookupName(categories, item.categoryId) ?? "—"}</TableCell>
                        <TableCell className="text-right tabular-nums">
                          {formatCents(item.amountCents)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              )}
            </CardContent>
          </Card>

          {(detail?.payments.length ?? 0) > 0 && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">{t("statement.payments")}</CardTitle>
              </CardHeader>
              <CardContent>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t("statement.paymentDate")}</TableHead>
                      <TableHead>{t("statement.paymentAccount")}</TableHead>
                      <TableHead className="text-right">{t("statement.paymentAmount")}</TableHead>
                      <TableHead className="text-right">{t("common.actions")}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {(detail?.payments ?? []).map((payment) => (
                      <TableRow key={payment.id}>
                        <TableCell className="tabular-nums">
                          {formatDate(payment.paymentDate)}
                        </TableCell>
                        <TableCell>{lookupName(accounts, payment.accountId) ?? "—"}</TableCell>
                        <TableCell className="text-right tabular-nums">
                          {formatCents(payment.amountCents)}
                        </TableCell>
                        <TableCell className="text-right">
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label={t("statement.reverseAction", {
                              amount: formatCents(payment.amountCents),
                            })}
                            onClick={() => setReversing(payment.id)}
                          >
                            <Undo2 className="size-4" />
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
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
        </>
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
