import { useTranslation } from "react-i18next";
import { EllipsisVertical, Pencil, RotateCcw, Trash2, CircleCheck } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useFormatCents } from "@/hooks/useFormatCents";
import { formatDate } from "@/lib/format";
import type { Position } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { formatPercent } from "@/features/simulator/simulatorFormat";
import { daysBetween } from "@/features/investments/positionForm";
import { todayIso } from "@/features/transactions/transactionUtils";

interface PositionCardProps {
  position: Position;
  onSettle: (position: Position) => void;
  onEdit: (position: Position) => void;
  onDelete: (position: Position) => void;
  onUnsettle: (position: Position) => void;
}

function Stat({ label, value, testId }: { label: string; value: string; testId?: string }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <span className="text-[11px] text-ink-tertiary">{label}</span>
      <span className="text-[14px] font-extrabold tabular-nums text-ink-primary" data-testid={testId}>
        {value}
      </span>
    </div>
  );
}

/** "110% do CDI", "12,00% a.a." */
function rateLabel(position: Position, t: (key: string, options?: Record<string, unknown>) => string): string {
  const value = formatPercent(position.rate);
  switch (position.indexer) {
    case "CDI_PERCENT":
      return t("investments.card.percentOfCdi", { value });
    case "SELIC_PERCENT":
      return t("investments.card.percentOfSelic", { value });
    default:
      return t("investments.card.perYear", { value });
  }
}

export function PositionCard({ position, onSettle, onEdit, onDelete, onUnsettle }: PositionCardProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const settled = position.status === "SETTLED";
  const daysToMaturity = daysBetween(todayIso(), position.maturityDate);
  const title = position.name || t(`simulator.instrument.${position.type}`);

  const maturityText = settled
    ? t("investments.card.paidOn", { date: formatDate(position.settlement?.settledDate ?? position.maturityDate) })
    : position.awaitingPayment
      ? t("investments.card.dueSince", { date: formatDate(position.maturityDate) })
      : t("investments.card.matures", { date: formatDate(position.maturityDate), days: daysToMaturity });

  return (
    <div className="flex min-w-0 flex-col gap-3 rounded-card bg-card p-4" data-testid="position-card">
      <div className="flex items-start gap-2">
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <span className="truncate text-[14px] font-extrabold tracking-tight text-ink-primary">{title}</span>
          <span className="truncate text-[11px] text-ink-tertiary">
            {t(`simulator.instrument.${position.type}`)} · {rateLabel(position, t)} · {position.accountName}
            {position.vaultName ? ` · ${position.vaultName}` : ""}
          </span>
        </div>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon-sm" className="shrink-0 rounded-pill text-ink-secondary" aria-label={t("common.actions")}>
              <EllipsisVertical className="size-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            {!settled && (
              <>
                <DropdownMenuItem onSelect={() => onSettle(position)}>
                  <CircleCheck className="size-4" />
                  {t("investments.actions.settle")}
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={() => onEdit(position)}>
                  <Pencil className="size-4" />
                  {t("investments.actions.edit")}
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={() => onDelete(position)}>
                  <Trash2 className="size-4" />
                  {t("investments.actions.delete")}
                </DropdownMenuItem>
              </>
            )}
            {settled && (
              <DropdownMenuItem onSelect={() => onUnsettle(position)}>
                <RotateCcw className="size-4" />
                {t("investments.actions.unsettle")}
              </DropdownMenuItem>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {(position.awaitingPayment || settled || position.origin === "MANUAL") && (
        <div className="flex flex-wrap items-center gap-1.5">
          {position.awaitingPayment && <Badge variant="alert">{t("investments.card.awaiting")}</Badge>}
          {settled && <Badge variant="success">{t("investments.card.settled")}</Badge>}
          {position.origin === "MANUAL" && <Badge variant="neutral">{t("investments.card.beforeApp")}</Badge>}
        </div>
      )}

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat label={t("investments.card.applied")} value={formatCents(position.principalCents)} />
        {settled && position.settlement ? (
          <>
            <Stat label={t("investments.card.received")} value={formatCents(position.settlement.receivedCents)} testId="received" />
            <Stat label={t("investments.card.yield")} value={formatCents(position.settlement.yieldCents ?? 0)} />
            {position.settlement.estimatedNetCents != null && (
              <Stat label={t("investments.card.estimated")} value={formatCents(position.settlement.estimatedNetCents)} />
            )}
          </>
        ) : position.estimate ? (
          <>
            <Stat label={t("investments.card.valueToday")} value={formatCents(position.estimate.today.netCents)} testId="value-today" />
            <Stat label={t("investments.card.accrued")} value={formatCents(position.estimate.today.netYieldCents)} />
            <Stat label={t("investments.card.atMaturity")} value={formatCents(position.estimate.atMaturity.netCents)} />
          </>
        ) : (
          <p className="col-span-3 self-end text-[11px] text-ink-tertiary">{t("investments.card.noEstimate")}</p>
        )}
      </div>

      <div className="flex flex-wrap items-center justify-between gap-2 border-t border-divider-card pt-2.5 text-[11px] text-ink-tertiary">
        <span>{maturityText}</span>
        {!settled && position.estimate?.today.lastIndexDate && (
          <span>{t("investments.card.indexUpTo", { date: formatDate(position.estimate.today.lastIndexDate) })}</span>
        )}
      </div>
    </div>
  );
}
