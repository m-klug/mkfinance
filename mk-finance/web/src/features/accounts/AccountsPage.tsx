import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Archive, Pencil, Plus } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { formatCents } from "@/lib/format";
import { translateApiError } from "@/lib/errors";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import {
  getListAccountsQueryKey,
  useArchiveAccount,
  useListAccounts,
} from "@/lib/api/generated/accounts/accounts";
import { AccountType, type Account } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { AccountFormDialog } from "@/features/accounts/AccountFormDialog";
import {
  effectiveBalanceCents,
  projectedBalanceCents,
} from "@/features/accounts/accountOptions";

function sumBy(accounts: Account[], value: (account: Account) => number): number {
  return accounts.reduce((total, account) => total + value(account), 0);
}

export default function AccountsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const workspaceId = useCurrentWorkspaceId();
  const [includeArchived, setIncludeArchived] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Account | undefined>();
  const [archiving, setArchiving] = useState<Account | undefined>();

  const { data: accounts, isPending } = useListAccounts(
    workspaceId ?? "",
    { includeArchived },
    { query: { enabled: Boolean(workspaceId) } },
  );

  const archiveMutation = useArchiveAccount();

  // R5: current balance excludes pending; the projected one includes it. The
  // "without investments" total is what the dashboard calls spendable money.
  const totals = useMemo(() => {
    const active = (accounts ?? []).filter((account) => !account.archived);
    const spendable = active.filter((account) => account.type !== AccountType.INVESTMENT);
    return {
      balance: sumBy(spendable, effectiveBalanceCents),
      balanceWithInvestments: sumBy(active, effectiveBalanceCents),
      projected: sumBy(spendable, projectedBalanceCents),
    };
  }, [accounts]);

  function openCreate() {
    setEditing(undefined);
    setFormOpen(true);
  }

  function openEdit(account: Account) {
    setEditing(account);
    setFormOpen(true);
  }

  function confirmArchive() {
    if (!workspaceId || !archiving) return;
    archiveMutation.mutate(
      { workspaceId, accountId: archiving.id },
      {
        onSuccess: () => {
          void queryClient.invalidateQueries({ queryKey: getListAccountsQueryKey(workspaceId) });
          toast.success(t("accounts.archived"));
          setArchiving(undefined);
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
        <h1 className="text-2xl font-semibold">{t("nav.accounts")}</h1>
        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2 text-sm text-muted-foreground">
            <input
              type="checkbox"
              className="size-4"
              checked={includeArchived}
              onChange={(event) => setIncludeArchived(event.target.checked)}
            />
            {t("accounts.showArchived")}
          </label>
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            {t("accounts.new")}
          </Button>
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardHeader>
            <CardDescription>{t("accounts.totalBalance")}</CardDescription>
            <CardTitle className="text-2xl">{formatCents(totals.balance)}</CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>{t("accounts.totalBalanceWithInvestments")}</CardDescription>
            <CardTitle className="text-2xl">
              {formatCents(totals.balanceWithInvestments)}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card>
          <CardHeader>
            <CardDescription>{t("accounts.projectedBalance")}</CardDescription>
            <CardTitle className="text-2xl">{formatCents(totals.projected)}</CardTitle>
          </CardHeader>
        </Card>
      </div>

      <Card>
        <CardContent>
          {isPending && <p className="text-muted-foreground">{t("common.loading")}</p>}
          {!isPending && (accounts?.length ?? 0) === 0 && (
            <p className="text-muted-foreground">{t("accounts.empty")}</p>
          )}
          {!isPending && (accounts?.length ?? 0) > 0 && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("accounts.name")}</TableHead>
                  <TableHead>{t("accounts.type")}</TableHead>
                  <TableHead className="text-right">{t("accounts.balance")}</TableHead>
                  <TableHead className="text-right">{t("accounts.projectedBalance")}</TableHead>
                  <TableHead className="text-right">{t("common.actions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(accounts ?? []).map((account) => (
                  <TableRow key={account.id}>
                    <TableCell className="font-medium">
                      {account.name}
                      {account.archived && (
                        <span className="ml-2 rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                          {t("accounts.archivedBadge")}
                        </span>
                      )}
                    </TableCell>
                    <TableCell>{t(`accountType.${account.type}`)}</TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatCents(effectiveBalanceCents(account))}
                    </TableCell>
                    <TableCell className="text-right tabular-nums text-muted-foreground">
                      {formatCents(projectedBalanceCents(account))}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          aria-label={t("accounts.editAction", { name: account.name })}
                          onClick={() => openEdit(account)}
                        >
                          <Pencil className="size-4" />
                        </Button>
                        {!account.archived && (
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label={t("accounts.archiveAction", { name: account.name })}
                            onClick={() => setArchiving(account)}
                          >
                            <Archive className="size-4" />
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      {workspaceId && (
        <AccountFormDialog
          workspaceId={workspaceId}
          open={formOpen}
          onOpenChange={setFormOpen}
          account={editing}
        />
      )}

      <ConfirmDialog
        open={Boolean(archiving)}
        onOpenChange={(open) => !open && setArchiving(undefined)}
        title={t("accounts.archiveTitle")}
        description={t("accounts.archiveDescription", { name: archiving?.name ?? "" })}
        confirmLabel={t("accounts.archiveConfirm")}
        pending={archiveMutation.isPending}
        onConfirm={confirmArchive}
      />
    </div>
  );
}
