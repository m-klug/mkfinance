import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Repeat } from "lucide-react";

import { Card, CardContent } from "@/components/ui/card";
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
        <h1 className="text-2xl font-semibold">{t("nav.calendar")}</h1>
        <MonthNavigator month={month} onMonthChange={setMonth} />
      </div>

      {isPending && <p className="text-muted-foreground">{t("common.loading")}</p>}

      {!isPending && isError && (
        <p role="alert" className="text-sm text-destructive">
          {translateApiError(t, error)}
        </p>
      )}

      {!isPending && !isError && (
        <div className="flex flex-col gap-6">
          <Card>
            <CardContent>
              <div className="grid grid-cols-7 gap-1 text-center text-xs text-muted-foreground">
                {WEEKDAY_SHORT_LABELS.map((label) => (
                  <div key={label} className="py-1 capitalize">
                    {label}
                  </div>
                ))}
              </div>
              <div className="grid grid-cols-7 gap-1">
                {grid.flat().map((cell) => {
                  const dayEntries = groups.get(cell.date) ?? [];
                  const hasRecurrence = dayEntries.some((entry) => Boolean(entry.recurrenceId));
                  return (
                    <div
                      key={cell.date}
                      className={cn(
                        "flex min-h-14 flex-col items-center gap-1 rounded-md border p-1 text-xs sm:min-h-20 sm:p-2",
                        !cell.inMonth && "border-transparent text-muted-foreground/50",
                        cell.isToday && "border-primary",
                      )}
                    >
                      <span className={cn("font-medium", cell.isToday && "text-primary")}>
                        {cell.day}
                      </span>
                      {dayEntries.length > 0 && (
                        <span className="flex items-center gap-1 rounded-full bg-muted px-1.5 py-0.5 text-[10px] font-medium text-muted-foreground">
                          {dayEntries.length}
                          {hasRecurrence && <Repeat className="size-2.5" aria-hidden />}
                        </span>
                      )}
                    </div>
                  );
                })}
              </div>
            </CardContent>
          </Card>

          {entries.length === 0 && (
            <p className="text-muted-foreground">{t("calendar.empty")}</p>
          )}

          {entries.length > 0 && (
            <div className="flex flex-col gap-4">
              {[...groups.entries()].map(([date, dayEntries]) => (
                <Card key={date}>
                  <CardContent className="flex flex-col gap-3">
                    <div className="flex items-center gap-2">
                      <h2 className="text-sm font-semibold capitalize">{formatDate(date)}</h2>
                      {date === today && (
                        <span className="rounded bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                          {t("calendar.today")}
                        </span>
                      )}
                    </div>
                    <ul className="flex flex-col divide-y">
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

  return (
    <li className="flex flex-wrap items-center justify-between gap-2 py-2 text-sm">
      <div className="flex min-w-0 flex-col gap-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="truncate font-medium">{entry.description}</span>
          <span className="rounded bg-muted px-2 py-0.5 text-xs text-muted-foreground">
            {t(`calendarEntryKind.${entry.kind}`)}
          </span>
          {entry.kind === CalendarEntryKind.PENDING_TRANSACTION && entry.recurrenceId && (
            <Link
              to="/recurrences"
              className="flex items-center gap-1 rounded bg-secondary px-2 py-0.5 text-xs text-secondary-foreground hover:underline"
            >
              <Repeat className="size-3" aria-hidden />
              {t("calendar.recurrenceBadge")}
            </Link>
          )}
        </div>
      </div>
      <span className={cn("tabular-nums font-medium", outflow ? "text-destructive" : "text-emerald-600 dark:text-emerald-400")}>
        {formatCents(signedAmountCents(entry))}
      </span>
    </li>
  );
}
