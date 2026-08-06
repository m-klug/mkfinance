import { useMemo, useState } from "react";
import { Link, useParams } from "react-router";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  Archive,
  ArrowLeft,
  ArrowLeftRight,
  Pencil,
  Plus,
  Receipt,
  Trash2,
  TrendingUp,
} from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { cn } from "@/lib/utils";
import { formatCents } from "@/lib/format";
import { translateApiError } from "@/lib/errors";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import { getListAccountsQueryKey, useListAccounts } from "@/lib/api/generated/accounts/accounts";
import {
  getListVaultsQueryKey,
  useArchiveVault,
  useDeleteVault,
  useListVaults,
} from "@/lib/api/generated/vaults/vaults";
import type { Vault } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { effectiveBalanceCents } from "@/features/accounts/accountOptions";
import { VaultCompositionCard } from "@/features/vaults/VaultCompositionCard";
import { VaultFormDialog } from "@/features/vaults/VaultFormDialog";
import { VaultGoalBar } from "@/features/vaults/VaultGoalBar";
import { VaultReallocateDialog } from "@/features/vaults/VaultReallocateDialog";
import { VaultEntriesSheet } from "@/features/vaults/VaultEntriesSheet";
import { VaultYieldDialog } from "@/features/vaults/VaultYieldDialog";

export default function VaultsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const workspaceId = useCurrentWorkspaceId();
  const { accountId = "" } = useParams();

  const [includeArchived, setIncludeArchived] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Vault | undefined>();
  const [archiving, setArchiving] = useState<Vault | undefined>();
  const [deleting, setDeleting] = useState<Vault | undefined>();
  const [reallocateOpen, setReallocateOpen] = useState(false);
  const [yieldOpen, setYieldOpen] = useState(false);
  const [viewingEntriesOf, setViewingEntriesOf] = useState<Vault | undefined>();

  const enabled = Boolean(workspaceId) && Boolean(accountId);

  // includeArchived: true so the header still resolves for an archived
  // account, the same reasoning CardStatementsPage uses for archived cards.
  const { data: accountsData } = useListAccounts(
    workspaceId ?? "",
    { includeArchived: true },
    { query: { enabled } },
  );
  const account = useMemo(
    () => (accountsData ?? []).find((item) => item.id === accountId),
    [accountsData, accountId],
  );

  const { data: composition, isPending: listPending } = useListVaults(
    workspaceId ?? "",
    accountId,
    { includeArchived },
    { query: { enabled } },
  );
  const vaults = composition?.vaults ?? [];

  function invalidateComposition() {
    if (!workspaceId || !accountId) return;
    void queryClient.invalidateQueries({
      queryKey: getListVaultsQueryKey(workspaceId, accountId),
    });
  }

  // A yield also books an INCOME transaction that moves the account's own
  // balance (§4.5), shown in this page's header — the composition alone
  // would leave it stale.
  function invalidateAfterYield() {
    invalidateComposition();
    if (!workspaceId) return;
    void queryClient.invalidateQueries({ queryKey: getListAccountsQueryKey(workspaceId) });
  }

  function openCreate() {
    setEditing(undefined);
    setFormOpen(true);
  }

  function openEdit(vault: Vault) {
    setEditing(vault);
    setFormOpen(true);
  }

  const archiveMutation = useArchiveVault();
  const deleteMutation = useDeleteVault();

  function confirmArchive() {
    if (!workspaceId || !archiving) return;
    archiveMutation.mutate(
      { workspaceId, vaultId: archiving.id },
      {
        onSuccess: () => {
          invalidateComposition();
          toast.success(t("vaults.archived"));
          setArchiving(undefined);
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
      { workspaceId, vaultId: deleting.id },
      {
        onSuccess: () => {
          invalidateComposition();
          toast.success(t("vaults.deleted"));
          setDeleting(undefined);
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
            aria-label={t("vaults.backToAccounts")}
          >
            <Link to="/accounts">
              <ArrowLeft className="size-4" />
            </Link>
          </Button>
          <h1 className="text-2xl font-semibold">{account?.name ?? t("vaults.title")}</h1>
        </div>
        {account && (
          <span className="text-sm text-muted-foreground">
            {t("vaults.balance")}: {formatCents(effectiveBalanceCents(account))}
          </span>
        )}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <input
            type="checkbox"
            className="size-4"
            checked={includeArchived}
            onChange={(event) => setIncludeArchived(event.target.checked)}
          />
          {t("vaults.showArchived")}
        </label>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" onClick={() => setYieldOpen(true)}>
            <TrendingUp className="size-4" />
            {t("vaults.yieldAction")}
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => setReallocateOpen(true)}
            disabled={vaults.filter((vault) => !vault.archived).length === 0}
          >
            <ArrowLeftRight className="size-4" />
            {t("vaults.reallocate")}
          </Button>
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            {t("vaults.new")}
          </Button>
        </div>
      </div>

      {listPending && <p className="text-muted-foreground">{t("common.loading")}</p>}

      {composition && <VaultCompositionCard composition={composition} />}

      {!listPending && vaults.length === 0 && (
        <Card>
          <CardContent>
            <p className="text-muted-foreground">{t("vaults.empty")}</p>
          </CardContent>
        </Card>
      )}

      {vaults.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2">
          {vaults.map((vault) => (
            <Card key={vault.id}>
              <CardHeader className="gap-2">
                <div className="flex items-center justify-between gap-2">
                  <CardTitle className="flex items-center gap-2 text-base">
                    <span
                      className="size-3 rounded-full"
                      style={{ backgroundColor: vault.color ?? undefined }}
                      aria-hidden
                    />
                    {vault.name}
                    {vault.archived && (
                      <span className="rounded bg-muted px-2 py-0.5 text-xs font-normal text-muted-foreground">
                        {t("cards.archivedBadge")}
                      </span>
                    )}
                  </CardTitle>
                  <div className="flex items-center gap-1">
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label={t("vaults.entriesAction", { name: vault.name })}
                      onClick={() => setViewingEntriesOf(vault)}
                    >
                      <Receipt className="size-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label={t("vaults.editAction", { name: vault.name })}
                      onClick={() => openEdit(vault)}
                    >
                      <Pencil className="size-4" />
                    </Button>
                    {!vault.archived && (
                      <Button
                        variant="ghost"
                        size="icon-sm"
                        aria-label={t("vaults.archiveAction", { name: vault.name })}
                        onClick={() => setArchiving(vault)}
                      >
                        <Archive className="size-4" />
                      </Button>
                    )}
                    <Button
                      variant="ghost"
                      size="icon-sm"
                      aria-label={t("vaults.deleteAction", { name: vault.name })}
                      onClick={() => setDeleting(vault)}
                    >
                      <Trash2 className="size-4" />
                    </Button>
                  </div>
                </div>
                <dl className={cn("grid grid-cols-2 gap-x-4 gap-y-1 text-sm")}>
                  <div>
                    <dt className="text-muted-foreground">{t("vaults.balance")}</dt>
                    <dd className="tabular-nums font-medium">
                      {formatCents(vault.balanceCents)}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-muted-foreground">{t("vaults.projectedBalance")}</dt>
                    <dd className="tabular-nums text-muted-foreground">
                      {formatCents(vault.projectedBalanceCents)}
                    </dd>
                  </div>
                </dl>
                {vault.goal && (
                  <VaultGoalBar goal={vault.goal} currentCents={vault.balanceCents} />
                )}
              </CardHeader>
            </Card>
          ))}
        </div>
      )}

      {workspaceId && (
        <VaultFormDialog
          workspaceId={workspaceId}
          accountId={accountId}
          open={formOpen}
          onOpenChange={setFormOpen}
          vault={editing}
          onSaved={invalidateComposition}
        />
      )}

      {workspaceId && (
        <VaultReallocateDialog
          workspaceId={workspaceId}
          vaults={vaults}
          open={reallocateOpen}
          onOpenChange={setReallocateOpen}
          onReallocated={invalidateComposition}
        />
      )}

      {workspaceId && (
        <VaultYieldDialog
          workspaceId={workspaceId}
          accountId={accountId}
          vaults={vaults}
          open={yieldOpen}
          onOpenChange={setYieldOpen}
          onCreated={invalidateAfterYield}
        />
      )}

      {workspaceId && (
        <VaultEntriesSheet
          workspaceId={workspaceId}
          vault={viewingEntriesOf}
          open={Boolean(viewingEntriesOf)}
          onOpenChange={(open) => !open && setViewingEntriesOf(undefined)}
        />
      )}

      <ConfirmDialog
        open={Boolean(archiving)}
        onOpenChange={(open) => !open && setArchiving(undefined)}
        title={t("vaults.archiveTitle")}
        description={t("vaults.archiveDescription", { name: archiving?.name ?? "" })}
        confirmLabel={t("vaults.archiveConfirm")}
        pending={archiveMutation.isPending}
        onConfirm={confirmArchive}
      />

      <ConfirmDialog
        open={Boolean(deleting)}
        onOpenChange={(open) => !open && setDeleting(undefined)}
        title={t("vaults.deleteTitle")}
        description={t("vaults.deleteDescription", { name: deleting?.name ?? "" })}
        confirmLabel={t("vaults.deleteConfirm")}
        destructive
        pending={deleteMutation.isPending}
        onConfirm={confirmDelete}
      />
    </div>
  );
}
