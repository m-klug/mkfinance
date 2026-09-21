import { useState } from "react";
import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Archive, Pencil, Plus, Receipt } from "lucide-react";

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
import { cn } from "@/lib/utils";
import { useFormatCents } from "@/hooks/useFormatCents";
import { translateApiError } from "@/lib/errors";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import {
  getListCardsQueryKey,
  useArchiveCard,
  useListCards,
} from "@/lib/api/generated/cards/cards";
import type { Card as CardModel } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { CardFormDialog } from "@/features/cards/CardFormDialog";
import { availableLimitCents, usagePercent } from "@/features/cards/cardOptions";

export default function CardsPage() {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
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
    <div className="flex flex-1 flex-col gap-4 p-4 md:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-extrabold tracking-tight text-ink-primary">
          {t("nav.cards")}
        </h1>
        <div className="flex items-center gap-2.5">
          <label className="flex items-center gap-2 rounded-pill bg-card px-3.5 py-2 text-xs font-semibold text-ink-secondary">
            <input
              type="checkbox"
              className="size-4 accent-mint"
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

      <Card className="rounded-card p-2 sm:p-3">
        <CardContent className="p-0">
          {isPending && <p className="p-3 text-sm text-ink-tertiary">{t("common.loading")}</p>}
          {!isPending && (cards?.length ?? 0) === 0 && (
            <p className="p-3 text-sm text-ink-tertiary">{t("cards.empty")}</p>
          )}
          {!isPending && (cards?.length ?? 0) > 0 && (
            <Table>
              <TableHeader>
                <TableRow className="border-divider-card hover:bg-transparent">
                  <TableHead className="text-[11px] font-bold text-ink-label">
                    {t("cards.name")}
                  </TableHead>
                  <TableHead className="text-right text-[11px] font-bold text-ink-label">
                    {t("cards.limit")}
                  </TableHead>
                  <TableHead className="text-right text-[11px] font-bold text-ink-label">
                    {t("cards.availableLimit")}
                  </TableHead>
                  <TableHead className="text-center text-[11px] font-bold text-ink-label">
                    {t("cards.closingDueShort")}
                  </TableHead>
                  <TableHead className="text-right text-[11px] font-bold text-ink-label">
                    {t("common.actions")}
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(cards ?? []).map((card) => {
                  const usage = usagePercent(card);
                  return (
                    <TableRow key={card.id} className="border-divider-row">
                      <TableCell className="font-medium">
                        <div className="flex min-w-0 items-center gap-2.5">
                          <span
                            className="h-[24px] w-[36px] shrink-0 rounded-[6px] bg-mint"
                            aria-hidden
                          />
                          <span className="flex min-w-0 flex-col">
                            <span className="min-w-0 truncate text-[13px] font-bold text-ink-primary">
                              {card.name}
                            </span>
                            {card.brand && (
                              <span className="text-[10px] text-ink-label">{card.brand}</span>
                            )}
                          </span>
                          {card.archived && (
                            <Badge variant="neutral" className="shrink-0">
                              {t("cards.archivedBadge")}
                            </Badge>
                          )}
                        </div>
                      </TableCell>
                      <TableCell className="text-right tabular-nums text-ink-primary">
                        {formatCents(card.limitCents)}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex flex-col items-end gap-1">
                          <span className="tabular-nums text-[13px] font-bold text-ink-primary">
                            {formatCents(availableLimitCents(card))}
                          </span>
                          <span className="relative block h-[6px] w-full max-w-24 overflow-hidden rounded-pill bg-neutral">
                            <span
                              className={cn(
                                "absolute inset-y-0 left-0 block rounded-pill",
                                usage <= 70 ? "bg-mint" : "bg-pillar-pleasures",
                              )}
                              style={{ width: `${usage}%` }}
                            />
                          </span>
                        </div>
                      </TableCell>
                      <TableCell className="text-center tabular-nums text-ink-secondary">
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
                            className="rounded-pill bg-success-bg text-success-ink hover:bg-success-bg/80 hover:text-success-ink"
                            aria-label={t("cards.statementsAction", { name: card.name })}
                          >
                            <Link to={`/cards/${card.id}/statements`}>
                              <Receipt className="size-4" />
                            </Link>
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon-sm"
                            className="rounded-pill text-ink-secondary hover:bg-neutral"
                            aria-label={t("cards.editAction", { name: card.name })}
                            onClick={() => openEdit(card)}
                          >
                            <Pencil className="size-4" />
                          </Button>
                          {!card.archived && (
                            <Button
                              variant="ghost"
                              size="icon-sm"
                              className="rounded-pill text-ink-secondary hover:bg-neutral"
                              aria-label={t("cards.archiveAction", { name: card.name })}
                              onClick={() => setArchiving(card)}
                            >
                              <Archive className="size-4" />
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
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
