import { useTranslation } from "react-i18next";

import { cn } from "@/lib/utils";
import { Badge } from "@/components/ui/badge";
import { formatDate } from "@/lib/format";
import { useFormatCents } from "@/hooks/useFormatCents";
import type { Card as CreditCard, Statement } from "@/lib/api/generated/mKFinanceAPI.schemas";

interface OpenStatementsSectionProps {
  statements: Statement[];
  cards: CreditCard[];
}

const DUE_SOON_DAYS = 7;

/** Whole days from local today to an ISO date; negative when already past. */
function daysUntil(isoDate: string, today: Date = new Date()): number {
  const [year, month, day] = isoDate.split("-").map(Number);
  const due = new Date(year, month - 1, day);
  const start = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((due.getTime() - start.getTime()) / 86_400_000);
}

export function OpenStatementsSection({ statements, cards }: OpenStatementsSectionProps) {
  const { t } = useTranslation();
  const formatCents = useFormatCents();
  const cardName = new Map(cards.map((card) => [card.id, card.name]));
  const ordered = [...statements].sort((a, b) => a.dueDate.localeCompare(b.dueDate));
  const totalRemaining = ordered.reduce(
    (sum, statement) => sum + (statement.totalCents - statement.paidCents),
    0,
  );

  return (
    <div className="flex flex-col gap-4 rounded-card bg-card p-5">
      <div className="flex items-start justify-between gap-2">
        <div className="flex flex-col gap-0.5">
          <h3 className="text-sm font-bold text-ink-primary">{t("dashboard.statements.title")}</h3>
          <p className="text-xs text-ink-tertiary">{t("dashboard.statements.subtitle")}</p>
        </div>
        {ordered.length > 0 && (
          <span className="shrink-0 rounded-pill bg-neutral px-2.5 py-1 text-[11px] font-bold text-ink-secondary">
            {t("dashboard.statements.cardsCount", { count: cards.length })}
          </span>
        )}
      </div>

      {ordered.length === 0 ? (
        <p className="text-sm text-ink-tertiary">{t("dashboard.statements.empty")}</p>
      ) : (
        <>
          <ul className="flex flex-col gap-3">
            {ordered.map((statement) => {
              const remaining = statement.totalCents - statement.paidCents;
              const days = daysUntil(statement.dueDate);
              const dueSoon = days >= 0 && days <= DUE_SOON_DAYS;
              return (
                <li
                  key={statement.id}
                  className={cn(
                    "flex flex-col gap-2 rounded-list-item p-4",
                    dueSoon ? "bg-hero text-hero-foreground" : "bg-field",
                  )}
                >
                  <div className="flex items-center justify-between gap-2">
                    <span className="truncate text-[13px] font-bold">
                      {cardName.get(statement.cardId) ?? statement.referenceMonth}
                    </span>
                    {dueSoon && (
                      <Badge variant="alert" className="shrink-0 bg-[#3A201A] text-[#FFB4A2]">
                        {t("dashboard.statements.dueSoon")}
                      </Badge>
                    )}
                  </div>
                  <div>
                    <div className="text-xl font-extrabold tracking-[-0.02em] tabular-nums">
                      {formatCents(remaining)}
                    </div>
                    <div
                      className={cn(
                        "text-[11px]",
                        dueSoon ? "text-[#8D9298]" : "text-ink-tertiary",
                      )}
                    >
                      {t("dashboard.statements.dueOn", { date: formatDate(statement.dueDate) })}
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>

          <div className="mt-1 flex items-center justify-between border-t border-divider-card pt-3">
            <span className="text-xs text-ink-tertiary">{t("dashboard.statements.totalLabel")}</span>
            <span className="text-sm font-extrabold tabular-nums text-ink-primary">
              {formatCents(totalRemaining)}
            </span>
          </div>
        </>
      )}
    </div>
  );
}
