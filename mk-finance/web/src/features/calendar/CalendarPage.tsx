import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Repeat } from "lucide-react";

import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";
import { formatCents, formatDate } from "@/lib/format";
import { translateApiError } from "@/lib/errors";
import { useCurrentWorkspaceId } from "@/hooks/useCurrentWorkspace";
import { useGetCalendar } from "@/lib/api/generated/calendar/calendar";
import { CalendarEntryKind, type CalendarEntry } from "@/lib/api/generated/mKFinanceAPI.schemas";
import { currentMonth } from "@/features/dashboard/dashboardData";
import { MonthNavigator } from "@/features/dashboard/MonthNavigator";
import {
  WEEKDAY_SHORT_LABELS,
  buildMonthGrid,
  groupEntriesByDate,
  isOutflow,
  signedAmountCents,
  todayIso,
} from "@/features/calendar/calendarData";

/** A statement due date outranks a pending transaction for the day's tint. */
function dayTintClass(dayEntries: CalendarEntry[]): string {
  if (dayEntries.some((entry) => entry.kind === CalendarEntryKind.STATEMENT_DUE)) {
    return "bg-alert-bg";
  }
  if (dayEntries.length > 0) {
    return "bg-success-bg";
  }
  return "bg-field";
}

// Read-only agenda: no confirm/pay action lives here (PLANEJAMENTO decision) —
// settling a pending transaction still only happens from the extrato.
export default function CalendarPage() {
  const { t } = useTranslation();
  const workspaceId = useCurrentWorkspaceId();
  const [month, setMonth] = useState(() => currentMonth());
  const enabled = Boolean(workspaceId);

  const { data, isPending, isError, error } = useGetCalendar(
    workspaceId ?? "",
    { month },
    { query: { enabled } },
  );

  const entries = data?.entries ?? [];
  const grid = buildMonthGrid(month);
  const groups = groupEntriesByDate(entries);
  const today = todayIso();

  return (
    <div className="flex flex-1 flex-col gap-6 p-4 md:p-8">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-[11px] font-bold uppercase tracking-[0.14em] text-ink-label">
            {t("nav.calendar")}
          </p>
          <h1 className="text-[26px] font-extrabold tracking-[-0.025em] text-ink-primary">
            {t("nav.calendar")}
          </h1>
        </div>
        <MonthNavigator month={month} onMonthChange={setMonth} />
      </div>

      {isPending && <p className="text-ink-tertiary">{t("common.loading")}</p>}

      {!isPending && isError && (
        <p role="alert" className="text-sm text-alert-ink">
          {translateApiError(t, error)}
        </p>
      )}

      {!isPending && !isError && (
        <div className="flex flex-col gap-6">
          <Card className="gap-3 rounded-card border border-divider-card bg-card p-5">
            <CardContent className="flex flex-col gap-3 p-0">
              <div className="grid grid-cols-7 gap-1.5 text-center text-[11px] font-bold uppercase tracking-[0.04em] text-ink-label">
                {WEEKDAY_SHORT_LABELS.map((label) => (
                  <div key={label} className="py-1 capitalize">
                    {label}
                  </div>
                ))}
              </div>
              <div className="grid grid-cols-7 gap-1.5">
                {grid.flat().map((cell) => {
                  const dayEntries = groups.get(cell.date) ?? [];
                  const hasRecurrence = dayEntries.some((entry) => Boolean(entry.recurrenceId));
                  return (
                    <div
                      key={cell.date}
                      className={cn(
                        "flex min-h-14 flex-col items-center gap-1 rounded-list-item p-1 text-xs sm:min-h-20 sm:p-2",
                        cell.inMonth ? dayTintClass(dayEntries) : "bg-transparent",
                        !cell.inMonth && "text-ink-tertiary/40",
                        cell.isToday && "ring-2 ring-inset ring-[color:var(--mint)]",
                      )}
                    >
                      <span
                        className={cn(
                          "font-bold text-ink-primary",
                          !cell.inMonth && "text-ink-tertiary/40",
                          cell.isToday && "text-[color:var(--link)]",
                        )}
                      >
                        {cell.day}
                      </span>
                      {dayEntries.length > 0 && (
                        <span className="flex items-center gap-1 rounded-pill bg-card px-1.5 py-0.5 text-[10px] font-bold text-ink-secondary">
                          {dayEntries.length}
                          {hasRecurrence && (
                            <span
                              className="size-1.5 rounded-full bg-[color:var(--mint)]"
                              aria-hidden
                            />
                          )}
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>

              <div className="flex flex-wrap items-center gap-4 border-t border-divider-card pt-3 text-xs text-ink-secondary">
                <span className="flex items-center gap-1.5">
                  <span className="size-2.5 rounded-[4px] bg-success-bg" aria-hidden />
                  {t("calendar.legend.pending")}
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="size-2.5 rounded-[4px] bg-alert-bg" aria-hidden />
                  {t("calendar.legend.statementDue")}
                </span>
                <span className="flex items-center gap-1.5">
                  <span className="size-1.5 rounded-full bg-[color:var(--mint)]" aria-hidden />
                  {t("calendar.legend.recurrence")}
                </span>
              </div>
            </CardContent>
          </Card>

          {entries.length === 0 && (
            <p className="text-ink-tertiary">{t("calendar.empty")}</p>
          )}

          {entries.length > 0 && (
            <div className="flex flex-col gap-3">
              {[...groups.entries()].map(([date, dayEntries]) => (
                <Card key={date} className="gap-3 rounded-card border border-divider-card bg-card p-5">
                  <CardContent className="flex flex-col gap-3 p-0">
                    <div className="flex items-center gap-2">
                      <h2 className="text-sm font-extrabold capitalize text-ink-primary">
                        {formatDate(date)}
                      </h2>
                      {date === today && <Badge variant="success">{t("calendar.today")}</Badge>}
                    </div>
                    <ul className="flex flex-col divide-y divide-divider-row">
                      {dayEntries.map((entry, index) => (
                        <CalendarEntryRow key={`${entry.kind}-${index}`} entry={entry} />
                      ))}
                    </ul>
                  </CardContent>
                </Card>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function CalendarEntryRow({ entry }: { entry: CalendarEntry }) {
  const { t } = useTranslation();
  const outflow = isOutflow(entry);
  const markerClass =
    entry.kind === CalendarEntryKind.STATEMENT_DUE ? "bg-alert-ink" : "bg-success-ink";

  return (
    <li className="flex flex-wrap items-center justify-between gap-2 py-2.5 text-sm">
      <div className="flex min-w-0 items-center gap-2.5">
        <span className={cn("size-2 shrink-0 rounded-full", markerClass)} aria-hidden />
        <div className="flex min-w-0 flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="truncate font-bold text-ink-primary">{entry.description}</span>
            <Badge variant="neutral">{t(`calendarEntryKind.${entry.kind}`)}</Badge>
            {entry.kind === CalendarEntryKind.PENDING_TRANSACTION && entry.recurrenceId && (
              <Link
                to="/recurrences"
                className="flex items-center gap-1 rounded-pill bg-neutral px-2 py-0.5 text-xs font-bold text-ink-secondary hover:text-ink-primary"
              >
                <Repeat className="size-3" aria-hidden />
                {t("calendar.recurrenceBadge")}
              </Link>
            )}
          </div>
        </div>
      </div>
      <span
        className={cn(
          "tabular-nums font-bold",
          outflow ? "text-alert-ink" : "text-success-ink",
        )}
      >
        {formatCents(signedAmountCents(entry))}
      </span>
    </li>
  );
}
