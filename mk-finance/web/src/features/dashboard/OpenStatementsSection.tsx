import { useTranslation } from "react-i18next";
import { CalendarClock } from "lucide-react";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { formatCents, formatDate } from "@/lib/format";
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
  const cardName = new Map(cards.map((card) => [card.id, card.name]));
  const ordered = [...statements].sort((a, b) => a.dueDate.localeCompare(b.dueDate));

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t("dashboard.statements.title")}</CardTitle>
        <CardDescription>{t("dashboard.statements.subtitle")}</CardDescription>
      </CardHeader>
      <CardContent>
        {ordered.length === 0 ? (
          <p className="text-sm text-muted-foreground">{t("dashboard.statements.empty")}</p>
        ) : (
          <ul className="flex flex-col divide-y">
            {ordered.map((statement) => {
              const remaining = statement.totalCents - statement.paidCents;
              const days = daysUntil(statement.dueDate);
              const dueSoon = days >= 0 && days <= DUE_SOON_DAYS;
              return (
                <li key={statement.id} className="flex items-center justify-between gap-3 py-2">
                  <div className="flex min-w-0 flex-col">
                    <span className="truncate text-sm font-medium">
                      {cardName.get(statement.cardId) ?? statement.referenceMonth}
                    </span>
                    <span className="flex items-center gap-1 text-xs text-muted-foreground">
                      {t("dashboard.statements.dueOn", { date: formatDate(statement.dueDate) })}
                      {dueSoon && (
                        <span className="flex items-center gap-1 rounded bg-amber-500/15 px-1.5 py-0.5 font-medium text-amber-700 dark:text-amber-400">
                          <CalendarClock className="size-3" aria-hidden />
                          {t("dashboard.statements.dueSoon")}
                        </span>
                      )}
                    </span>
                  </div>
                  <div className="flex flex-col items-end">
                    <span className="text-sm tabular-nums">{formatCents(remaining)}</span>
                    <span className="text-xs text-muted-foreground">
                      {t("dashboard.statements.remaining")}
                    </span>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}
