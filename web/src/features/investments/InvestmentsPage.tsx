import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Plus } from "lucide-react";

import { Button } from "@/components/ui/button";
import { SegmentedControl } from "@/components/ui/segmented-control";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import { translateApiError } from "@/lib/errors";
import { getListAccountsQueryKey } from "@/lib/api/generated/accounts/accounts";
import { getListVaultsQueryKey } from "@/lib/api/generated/vaults/vaults";
import {
  getGetInvestmentSummaryQueryKey,
  getListPositionsQueryKey,
  getListUnlinkedContributionsQueryKey,
  useDeletePosition,
  useListPositions,
  useUnsettlePosition,
} from "@/lib/api/generated/investments/investments";
import type { Position } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { PositionCard } from "@/features/investments/PositionCard";
import { PositionFormDialog } from "@/features/investments/PositionFormDialog";
import { PositionSettleDialog } from "@/features/investments/PositionSettleDialog";
import { InvestmentSummarySection } from "@/features/investments/InvestmentSummarySection";

type Tab = "ACTIVE" | "SETTLED";

export default function InvestmentsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const workspaceId = useCurrentWorkspaceId();
  const [tab, setTab] = useState<Tab>("ACTIVE");
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Position | undefined>();
  const [settling, setSettling] = useState<Position | null>(null);
  const [deleting, setDeleting] = useState<Position | undefined>();
  const [unsettling, setUnsettling] = useState<Position | undefined>();

  const { data: positions, isPending } = useListPositions(
    workspaceId ?? "",
    { status: tab },
    { query: { enabled: Boolean(workspaceId) } },
  );

  const deleteMutation = useDeletePosition();
  const unsettleMutation = useUnsettlePosition();

  function invalidateAll(accountId?: string) {
    if (!workspaceId) return;
    void queryClient.invalidateQueries({ queryKey: getListPositionsQueryKey(workspaceId) });
    void queryClient.invalidateQueries({ queryKey: getListUnlinkedContributionsQueryKey(workspaceId) });
    void queryClient.invalidateQueries({ queryKey: getGetInvestmentSummaryQueryKey(workspaceId) });
    if (accountId) {
      void queryClient.invalidateQueries({ queryKey: getListAccountsQueryKey(workspaceId) });
      void queryClient.invalidateQueries({ queryKey: getListVaultsQueryKey(workspaceId, accountId) });
    }
  }

  function openCreate() {
    setEditing(undefined);
    setFormOpen(true);
  }

  function openEdit(position: Position) {
    setEditing(position);
    setFormOpen(true);
  }

  function confirmDelete() {
    if (!workspaceId || !deleting) return;
    deleteMutation.mutate(
      { workspaceId, positionId: deleting.id },
      {
        onSuccess: () => {
          invalidateAll();
          toast.success(t("investments.deleted"));
          setDeleting(undefined);
        },
        onError: (error) => toast.error(translateApiError(t, error)),
      },
    );
  }

  function confirmUnsettle() {
    if (!workspaceId || !unsettling) return;
    const accountId = unsettling.accountId;
    unsettleMutation.mutate(
      { workspaceId, positionId: unsettling.id },
      {
        onSuccess: () => {
          invalidateAll(accountId);
          toast.success(t("investments.unsettled"));
          setUnsettling(undefined);
        },
        onError: (error) => toast.error(translateApiError(t, error)),
      },
    );
  }

  const list = positions ?? [];

  return (
    <div className="flex flex-1 flex-col gap-4 p-4 md:p-8">
      <div className="flex items-start justify-between gap-3">
        <div className="flex flex-col gap-1 pt-1">
          <h1 className="text-xl font-extrabold tracking-[-0.025em] text-ink-primary">{t("investments.title")}</h1>
          <p className="text-sm text-ink-tertiary">{t("investments.subtitle")}</p>
        </div>
        <Button className="shrink-0 rounded-pill" onClick={openCreate}>
          <Plus className="size-4" strokeWidth={2.5} />
          {t("investments.new")}
        </Button>
      </div>

      {workspaceId && <InvestmentSummarySection workspaceId={workspaceId} />}

      <SegmentedControl
        options={[
          { value: "ACTIVE", label: t("investments.tabs.active") },
          { value: "SETTLED", label: t("investments.tabs.settled") },
        ]}
        value={tab}
        onChange={setTab}
      />

      {isPending && workspaceId && <p className="text-sm text-ink-tertiary">{t("common.loading")}</p>}

      {!isPending && list.length === 0 && (
        <div className="rounded-card bg-card p-6 text-center">
          <p className="text-sm font-bold text-ink-primary">
            {t(tab === "ACTIVE" ? "investments.emptyActive" : "investments.emptySettled")}
          </p>
          {tab === "ACTIVE" && <p className="mt-1 text-xs text-ink-tertiary">{t("investments.emptyHint")}</p>}
        </div>
      )}

      <div className="grid min-w-0 gap-3 xl:grid-cols-2">
        {list.map((position) => (
          <PositionCard
            key={position.id}
            position={position}
            onSettle={setSettling}
            onEdit={openEdit}
            onDelete={setDeleting}
            onUnsettle={setUnsettling}
          />
        ))}
      </div>

      {workspaceId && (
        <>
          <PositionFormDialog
            workspaceId={workspaceId}
            open={formOpen}
            onOpenChange={setFormOpen}
            position={editing}
          />
          <PositionSettleDialog workspaceId={workspaceId} position={settling} onClose={() => setSettling(null)} />
        </>
      )}

      <ConfirmDialog
        open={Boolean(deleting)}
        onOpenChange={(open) => !open && setDeleting(undefined)}
        title={t("investments.deleteTitle")}
        description={t("investments.deleteDescription")}
        confirmLabel={t("common.delete")}
        destructive
        pending={deleteMutation.isPending}
        onConfirm={confirmDelete}
      />
      <ConfirmDialog
        open={Boolean(unsettling)}
        onOpenChange={(open) => !open && setUnsettling(undefined)}
        title={t("investments.unsettleTitle")}
        description={t("investments.unsettleDescription")}
        confirmLabel={t("investments.actions.unsettle")}
        destructive
        pending={unsettleMutation.isPending}
        onConfirm={confirmUnsettle}
      />
    </div>
  );
}
