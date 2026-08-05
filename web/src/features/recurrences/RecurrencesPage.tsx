import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Pencil, Plus, Trash2 } from "lucide-react";

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
        <h1 className="text-2xl font-semibold">{t("nav.recurrences")}</h1>
        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2 text-sm text-muted-foreground">
            <input
              type="checkbox"
              className="size-4"
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

      <Card>
        <CardContent>
          {isPending && <p className="text-muted-foreground">{t("common.loading")}</p>}
          {!isPending && (recurrences?.length ?? 0) === 0 && (
            <p className="text-muted-foreground">{t("recurrences.empty")}</p>
          )}
          {!isPending && (recurrences?.length ?? 0) > 0 && (
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t("recurrences.description")}</TableHead>
                    <TableHead>{t("recurrences.frequency")}</TableHead>
                    <TableHead>{t("recurrences.account")}</TableHead>
                    <TableHead>{t("recurrences.category")}</TableHead>
                    <TableHead className="text-right">{t("recurrences.amount")}</TableHead>
                    <TableHead className="text-right">{t("common.actions")}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {(recurrences ?? []).map((recurrence) => (
                    <TableRow key={recurrence.id}>
                      <TableCell className="font-medium">
                        {recurrence.description}
                        {!recurrence.active && (
                          <span className="ml-2 rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                            {t("recurrences.statusClosed")}
                          </span>
                        )}
                      </TableCell>
                      <TableCell>{scheduleLabel(recurrence, t)}</TableCell>
                      <TableCell>{lookupName(accountList, recurrence.accountId) ?? "—"}</TableCell>
                      <TableCell>{lookupName(categoryList, recurrence.categoryId) ?? "—"}</TableCell>
                      <TableCell className="text-right tabular-nums">
                        {formatCents(recurrence.amountCents)}
                      </TableCell>
                      <TableCell className="text-right">
                        {recurrence.active && (
                          <div className="flex justify-end gap-1">
                            <Button
                              variant="ghost"
                              size="icon-sm"
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
