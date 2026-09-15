import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Pencil, Plus, Repeat, Trash2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
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
import { formatCents } from "@/lib/format";
import { translateApiError } from "@/lib/errors";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import { useListAccounts } from "@/lib/api/generated/accounts/accounts";
import { useListCategories } from "@/lib/api/generated/categories/categories";
import {
  getListRecurrencesQueryKey,
  useDeleteRecurrence,
  useListRecurrences,
} from "@/lib/api/generated/recurrences/recurrences";
import { RecurrenceScope, type Recurrence } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { RecurrenceFormDialog } from "@/features/recurrences/RecurrenceFormDialog";
import { RecurrenceScopeDialog } from "@/features/recurrences/RecurrenceScopeDialog";
import { lookupName, mayHaveMaterialized, scheduleLabel } from "@/features/recurrences/recurrenceOptions";

interface ScopeTarget {
  recurrence: Recurrence;
  action: "edit" | "delete";
}

export default function RecurrencesPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const workspaceId = useCurrentWorkspaceId();
  const [showClosed, setShowClosed] = useState(false);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Recurrence | undefined>();
  const [formScope, setFormScope] = useState<RecurrenceScope | undefined>();
  const [formOccurrenceDate, setFormOccurrenceDate] = useState<string | undefined>();

  const [scopeTarget, setScopeTarget] = useState<ScopeTarget | undefined>();
  const [deleting, setDeleting] = useState<Recurrence | undefined>();

  const enabled = Boolean(workspaceId);
  const { data: recurrences, isPending } = useListRecurrences(
    workspaceId ?? "",
    { active: showClosed ? undefined : true },
    { query: { enabled } },
  );
  const { data: accounts } = useListAccounts(workspaceId ?? "", undefined, { query: { enabled } });
  const { data: categories } = useListCategories(workspaceId ?? "", undefined, { query: { enabled } });

  const deleteMutation = useDeleteRecurrence();

  function invalidateList() {
    if (!workspaceId) return;
    void queryClient.invalidateQueries({ queryKey: getListRecurrencesQueryKey(workspaceId) });
  }

  function openCreate() {
    setEditing(undefined);
    setFormScope(undefined);
    setFormOccurrenceDate(undefined);
    setFormOpen(true);
  }

  function openEdit(recurrence: Recurrence) {
    if (mayHaveMaterialized(recurrence)) {
      setScopeTarget({ recurrence, action: "edit" });
      return;
    }
    setEditing(recurrence);
    setFormScope(undefined);
    setFormOccurrenceDate(undefined);
    setFormOpen(true);
  }

  function openDelete(recurrence: Recurrence) {
    if (mayHaveMaterialized(recurrence)) {
      setScopeTarget({ recurrence, action: "delete" });
      return;
    }
    setDeleting(recurrence);
  }

  function handleScopeConfirm(scope: RecurrenceScope, occurrenceDate?: string) {
    if (!scopeTarget || !workspaceId) return;
    const { recurrence, action } = scopeTarget;

    if (action === "edit") {
      setEditing(recurrence);
      setFormScope(scope);
      setFormOccurrenceDate(occurrenceDate);
      setFormOpen(true);
      setScopeTarget(undefined);
      return;
    }

    deleteMutation.mutate(
      { workspaceId, recurrenceId: recurrence.id, params: { scope, occurrenceDate } },
      {
        onSuccess: () => {
          invalidateList();
          toast.success(t("recurrences.deleted"));
          setScopeTarget(undefined);
        },
        onError: (error) => {
          toast.error(translateApiError(t, error));
        },
      },
    );
  }

  function confirmPlainDelete() {
    if (!workspaceId || !deleting) return;
    deleteMutation.mutate(
      { workspaceId, recurrenceId: deleting.id },
      {
        onSuccess: () => {
          invalidateList();
          toast.success(t("recurrences.deleted"));
          setDeleting(undefined);
        },
        onError: (error) => {
          toast.error(translateApiError(t, error));
          setDeleting(undefined);
        },
      },
    );
  }

  const accountList = accounts ?? [];
  const categoryList = categories ?? [];

  return (
    <div className="flex flex-1 flex-col gap-6 p-4 md:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.14em] text-ink-label">
            {t("nav.recurrences")}
          </p>
          <h1 className="text-[26px] font-extrabold tracking-[-0.025em] text-ink-primary">
            {t("nav.recurrences")}
          </h1>
        </div>
        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2 text-sm font-medium text-ink-secondary">
            <input
              type="checkbox"
              className="size-4 accent-[color:var(--mint)]"
              checked={showClosed}
              onChange={(event) => setShowClosed(event.target.checked)}
            />
            {t("recurrences.showClosed")}
          </label>
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            {t("recurrences.new")}
          </Button>
        </div>
      </div>

      <Card className="gap-0 rounded-card border border-divider-card bg-card p-5">
        <CardContent className="p-0">
          {isPending && <p className="text-ink-tertiary">{t("common.loading")}</p>}
          {!isPending && (recurrences?.length ?? 0) === 0 && (
            <p className="text-ink-tertiary">{t("recurrences.empty")}</p>
          )}
          {!isPending && (recurrences?.length ?? 0) > 0 && (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow className="border-divider-row hover:bg-transparent">
                    <TableHead className="text-[11px] font-bold uppercase tracking-[0.04em] text-ink-label">
                      {t("recurrences.description")}
                    </TableHead>
                    <TableHead className="text-[11px] font-bold uppercase tracking-[0.04em] text-ink-label">
                      {t("recurrences.frequency")}
                    </TableHead>
                    <TableHead className="text-[11px] font-bold uppercase tracking-[0.04em] text-ink-label">
                      {t("recurrences.account")}
                    </TableHead>
                    <TableHead className="text-[11px] font-bold uppercase tracking-[0.04em] text-ink-label">
                      {t("recurrences.category")}
                    </TableHead>
                    <TableHead className="text-right text-[11px] font-bold uppercase tracking-[0.04em] text-ink-label">
                      {t("recurrences.amount")}
                    </TableHead>
                    <TableHead className="text-right text-[11px] font-bold uppercase tracking-[0.04em] text-ink-label">
                      {t("common.actions")}
                    </TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(recurrences ?? []).map((recurrence) => (
                    <TableRow key={recurrence.id} className="border-divider-row">
                      <TableCell className="py-3 font-bold text-ink-primary">
                        <span className="flex items-center gap-2">
                          <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-neutral text-ink-tertiary">
                            <Repeat className="size-3.5" aria-hidden />
                          </span>
                          {recurrence.description}
                          {!recurrence.active && (
                            <Badge variant="neutral">{t("recurrences.statusClosed")}</Badge>
                          )}
                        </span>
                      </TableCell>
                      <TableCell className="text-ink-secondary">
                        {scheduleLabel(recurrence, t)}
                      </TableCell>
                      <TableCell className="text-ink-secondary">
                        {lookupName(accountList, recurrence.accountId) ?? "—"}
                      </TableCell>
                      <TableCell className="text-ink-secondary">
                        {lookupName(categoryList, recurrence.categoryId) ?? "—"}
                      </TableCell>
                      <TableCell className="text-right font-bold tabular-nums text-ink-primary">
                        {formatCents(recurrence.amountCents)}
                      </TableCell>
                      <TableCell className="text-right">
                        {recurrence.active && (
                          <div className="flex justify-end gap-1">
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              className="rounded-full text-ink-tertiary hover:bg-neutral hover:text-ink-primary"
                              aria-label={t("recurrences.editAction", {
                                description: recurrence.description,
                              })}
                              onClick={() => openEdit(recurrence)}
                            >
                              <Pencil className="size-4" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              className="rounded-full text-ink-tertiary hover:bg-neutral hover:text-ink-primary"
                              aria-label={t("recurrences.deleteAction", {
                                description: recurrence.description,
                              })}
                              onClick={() => openDelete(recurrence)}
                            >
                              <Trash2 className="size-4" />
                            </Button>
                          </div>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      {workspaceId && (
        <RecurrenceFormDialog
          workspaceId={workspaceId}
          open={formOpen}
          onOpenChange={setFormOpen}
          recurrence={editing}
          accounts={accountList}
          categories={categoryList}
          scope={formScope}
          occurrenceDate={formOccurrenceDate}
        />
      )}

      <RecurrenceScopeDialog
        open={Boolean(scopeTarget)}
        onOpenChange={(open) => !open && setScopeTarget(undefined)}
        variant={scopeTarget?.action ?? "edit"}
        pending={deleteMutation.isPending}
        onConfirm={handleScopeConfirm}
      />

      <ConfirmDialog
        open={Boolean(deleting)}
        onOpenChange={(open) => !open && setDeleting(undefined)}
        title={t("recurrences.deleteTitle")}
        description={t("recurrences.deleteDescription", { description: deleting?.description ?? "" })}
        confirmLabel={t("recurrences.deleteConfirm")}
        destructive
        pending={deleteMutation.isPending}
        onConfirm={confirmPlainDelete}
      />
    </div>
  );
}
