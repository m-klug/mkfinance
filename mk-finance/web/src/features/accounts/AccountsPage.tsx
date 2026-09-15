import { useMemo, useState } from "react";
import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Archive, PiggyBank, Pencil, Plus } from "lucide-react";

import { Badge } from "@/components/ui/badge";
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
  accountAvatarTone,
  accountInitials,
  effectiveBalanceCents,
  isContributionAccount,
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
    <div className="flex flex-1 flex-col gap-4 p-4 md:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold tracking-tight text-ink-primary">
          {t("nav.accounts")}
        </h1>
        <div className="flex items-center gap-2.5">
          <label className="flex items-center gap-2 rounded-pill bg-card px-3.5 py-2 text-xs font-semibold text-ink-secondary">
            <input
              type="checkbox"
              className="size-4 accent-mint"
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

      <div className="grid gap-3 sm:grid-cols-3">
        <Card className="gap-1.5 rounded-card p-4">
          <CardHeader className="gap-1 p-0">
            <CardDescription className="text-[11px] font-semibold text-ink-tertiary">
              {t("accounts.totalBalance")}
            </CardDescription>
            <CardTitle className="tabular-nums text-[25px] font-extrabold tracking-tight text-ink-primary">
              {formatCents(totals.balance)}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card className="gap-1.5 rounded-card p-4">
          <CardHeader className="gap-1 p-0">
            <CardDescription className="text-[11px] font-semibold text-ink-tertiary">
              {t("accounts.totalBalanceWithInvestments")}
            </CardDescription>
            <CardTitle className="tabular-nums text-[25px] font-extrabold tracking-tight text-ink-primary">
              {formatCents(totals.balanceWithInvestments)}
            </CardTitle>
          </CardHeader>
        </Card>
        <Card className="gap-1.5 rounded-card border-none bg-hero p-4">
          <CardHeader className="gap-1 p-0">
            <CardDescription className="text-[11px] font-semibold text-[#8d9298]">
              {t("accounts.projectedBalance")}
            </CardDescription>
            <CardTitle className="tabular-nums text-[25px] font-extrabold tracking-tight text-hero-foreground">
              {formatCents(totals.projected)}
            </CardTitle>
          </CardHeader>
        </Card>
      </div>

      <Card className="rounded-card p-2 sm:p-3">
        <CardContent className="p-0">
          {isPending && <p className="p-3 text-sm text-ink-tertiary">{t("common.loading")}</p>}
          {!isPending && (accounts?.length ?? 0) === 0 && (
            <p className="p-3 text-sm text-ink-tertiary">{t("accounts.empty")}</p>
          )}
          {!isPending && (accounts?.length ?? 0) > 0 && (
            <Table>
              <TableHeader>
                <TableRow className="border-divider-card hover:bg-transparent">
                  <TableHead className="text-[11px] font-bold text-ink-label">
                    {t("accounts.name")}
                  </TableHead>
                  <TableHead className="text-[11px] font-bold text-ink-label">
                    {t("accounts.type")}
                  </TableHead>
                  <TableHead className="text-right text-[11px] font-bold text-ink-label">
                    {t("accounts.balance")}
                  </TableHead>
                  <TableHead className="text-right text-[11px] font-bold text-ink-label">
                    {t("accounts.projectedBalance")}
                  </TableHead>
                  <TableHead className="text-right text-[11px] font-bold text-ink-label">
                    {t("common.actions")}
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(accounts ?? []).map((account, index) => (
                  <TableRow key={account.id} className="border-divider-row">
                    <TableCell className="font-medium">
                      <div className="flex min-w-0 items-center gap-2.5">
                        <span
                          className="flex size-[30px] shrink-0 items-center justify-center rounded-[10px] text-[11px] font-extrabold text-mint-ink"
                          style={{ backgroundColor: accountAvatarTone(index) }}
                          aria-hidden
                        >
                          {accountInitials(account.name)}
                        </span>
                        <span className="min-w-0 truncate text-[13px] font-bold text-ink-primary">
                          {account.name}
                        </span>
                        {account.archived && (
                          <Badge variant="neutral" className="shrink-0">
                            {t("accounts.archivedBadge")}
                          </Badge>
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="text-ink-secondary">
                      {t(`accountType.${account.type}`)}
                    </TableCell>
                    <TableCell className="text-right tabular-nums font-bold text-ink-primary">
                      {formatCents(effectiveBalanceCents(account))}
                    </TableCell>
                    <TableCell className="text-right tabular-nums text-ink-secondary">
                      {formatCents(projectedBalanceCents(account))}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        {isContributionAccount(account) && (
                          <Button
                            asChild
                            variant="ghost"
                            size="icon-sm"
                            className="rounded-pill bg-success-bg text-success-ink hover:bg-success-bg/80 hover:text-success-ink"
                            aria-label={t("vaults.accountAction", { name: account.name })}
                          >
                            <Link to={`/accounts/${account.id}/vaults`}>
                              <PiggyBank className="size-4" />
                            </Link>
                          </Button>
                        )}
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          className="rounded-pill text-ink-secondary hover:bg-neutral"
                          aria-label={t("accounts.editAction", { name: account.name })}
                          onClick={() => openEdit(account)}
                        >
                          <Pencil className="size-4" />
                        </Button>
                        {!account.archived && (
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            className="rounded-pill text-ink-secondary hover:bg-neutral"
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
