import { useState } from "react";
import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Archive, Pencil, Plus, Receipt } from "lucide-react";

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
import {
  getListCardsQueryKey,
  useArchiveCard,
  useListCards,
} from "@/lib/api/generated/cards/cards";
import type { Card as CardModel } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { CardFormDialog } from "@/features/cards/CardFormDialog";
import { availableLimitCents } from "@/features/cards/cardOptions";

export default function CardsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const workspaceId = useCurrentWorkspaceId();
  const [includeArchived, setIncludeArchived] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<CardModel | undefined>();
  const [archiving, setArchiving] = useState<CardModel | undefined>();

  const { data: cards, isPending } = useListCards(
    workspaceId ?? "",
    { includeArchived },
    { query: { enabled: Boolean(workspaceId) } },
  );

  const archiveMutation = useArchiveCard();

  function openCreate() {
    setEditing(undefined);
    setFormOpen(true);
  }

  function openEdit(card: CardModel) {
    setEditing(card);
    setFormOpen(true);
  }

  function confirmArchive() {
    if (!workspaceId || !archiving) return;
    archiveMutation.mutate(
      { workspaceId, cardId: archiving.id },
      {
        onSuccess: () => {
          void queryClient.invalidateQueries({ queryKey: getListCardsQueryKey(workspaceId) });
          toast.success(t("cards.archived"));
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
        <h1 className="text-2xl font-semibold">{t("nav.cards")}</h1>
        <div className="flex items-center gap-4">
          <label className="flex items-center gap-2 text-sm text-muted-foreground">
            <input
              type="checkbox"
              className="size-4"
              checked={includeArchived}
              onChange={(event) => setIncludeArchived(event.target.checked)}
            />
            {t("cards.showArchived")}
          </label>
          <Button onClick={openCreate}>
            <Plus className="size-4" />
            {t("cards.new")}
          </Button>
        </div>
      </div>

      <Card>
        <CardContent>
          {isPending && <p className="text-muted-foreground">{t("common.loading")}</p>}
          {!isPending && (cards?.length ?? 0) === 0 && (
            <p className="text-muted-foreground">{t("cards.empty")}</p>
          )}
          {!isPending && (cards?.length ?? 0) > 0 && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("cards.name")}</TableHead>
                  <TableHead className="text-right">{t("cards.limit")}</TableHead>
                  <TableHead className="text-right">{t("cards.availableLimit")}</TableHead>
                  <TableHead className="text-center">{t("cards.closingDueShort")}</TableHead>
                  <TableHead className="text-right">{t("common.actions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(cards ?? []).map((card) => (
                  <TableRow key={card.id}>
                    <TableCell className="font-medium">
                      {card.name}
                      {card.brand && (
                        <span className="ml-2 text-xs text-muted-foreground">{card.brand}</span>
                      )}
                      {card.archived && (
                        <span className="ml-2 rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                          {t("cards.archivedBadge")}
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatCents(card.limitCents)}
                    </TableCell>
                    <TableCell className="text-right tabular-nums font-medium">
                      {formatCents(availableLimitCents(card))}
                    </TableCell>
                    <TableCell className="text-center tabular-nums text-muted-foreground">
                      {t("cards.closingDueValue", {
                        closingDay: card.closingDay,
                        dueDay: card.dueDay,
                      })}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button
                          asChild
                          variant="ghost"
                          size="icon-sm"
                          aria-label={t("cards.statementsAction", { name: card.name })}
                        >
                          <Link to={`/cards/${card.id}/statements`}>
                            <Receipt className="size-4" />
                          </Link>
                        </Button>
                        <Button
                          variant="ghost"
                          size="icon-sm"
                          aria-label={t("cards.editAction", { name: card.name })}
                          onClick={() => openEdit(card)}
                        >
                          <Pencil className="size-4" />
                        </Button>
                        {!card.archived && (
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label={t("cards.archiveAction", { name: card.name })}
                            onClick={() => setArchiving(card)}
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
        <CardFormDialog
          workspaceId={workspaceId}
          open={formOpen}
          onOpenChange={setFormOpen}
          card={editing}
        />
      )}

      <ConfirmDialog
        open={Boolean(archiving)}
        onOpenChange={(open) => !open && setArchiving(undefined)}
        title={t("cards.archiveTitle")}
        description={t("cards.archiveDescription", { name: archiving?.name ?? "" })}
        confirmLabel={t("cards.archiveConfirm")}
        pending={archiveMutation.isPending}
        onConfirm={confirmArchive}
      />
    </div>
  );
}
