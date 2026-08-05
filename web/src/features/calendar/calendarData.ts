import { CalendarEntryKind, TransferDirection, type CalendarEntry } from "@/lib/api/generated/mKFinanceAPI.schemas";

export interface CalendarDay {
  date: string;
  day: number;
  inMonth: boolean;
  isToday: boolean;
}

function toIsoDateLocal(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

export function todayIso(): string {
  return toIsoDateLocal(new Date());
}

// Short weekday headers ("dom", "seg", ...) computed from a known Sunday
// instead of a new i18n map — mirrors how formatMonth/formatDate already
// lean on Intl for pt-BR date copy without a translation key per label.
export const WEEKDAY_SHORT_LABELS: string[] = Array.from({ length: 7 }, (_, i) =>
  new Date(2023, 0, 1 + i).toLocaleDateString("pt-BR", { weekday: "short" }),
);

/**
 * A Sunday-first 6-or-5-week grid covering the whole month, padded with the
 * trailing days of the previous/next month so every week is complete.
 */
export function buildMonthGrid(month: string, today: string = todayIso()): CalendarDay[][] {
  const [year, monthNumber] = month.split("-").map(Number);
  const firstOfMonth = new Date(year, monthNumber - 1, 1);
  const startWeekday = firstOfMonth.getDay();
  const daysInMonth = new Date(year, monthNumber, 0).getDate();
  const totalCells = Math.ceil((startWeekday + daysInMonth) / 7) * 7;

  const days: CalendarDay[] = [];
  for (let i = 0; i < totalCells; i++) {
    const date = new Date(year, monthNumber - 1, 1 - startWeekday + i);
    const iso = toIsoDateLocal(date);
    days.push({
      date: iso,
      day: date.getDate(),
      inMonth: date.getMonth() === monthNumber - 1,
      isToday: iso === today,
    });
  }

  const weeks: CalendarDay[][] = [];
  for (let i = 0; i < days.length; i += 7) {
    weeks.push(days.slice(i, i + 7));
  }
  return weeks;
}

/** Entries grouped by date, in the ascending order the API already returns them in. */
export function groupEntriesByDate(entries: CalendarEntry[]): Map<string, CalendarEntry[]> {
  const groups = new Map<string, CalendarEntry[]>();
  for (const entry of entries) {
    const group = groups.get(entry.date);
    if (group) {
      group.push(entry);
    } else {
      groups.set(entry.date, [entry]);
    }
  }
  return groups;
}

/**
 * STATEMENT_DUE has no direction because the kind alone already means money
 * owed (CT-1); PENDING_TRANSACTION carries a direction that tells IN from OUT.
 */
export function isOutflow(entry: CalendarEntry): boolean {
  return entry.kind === CalendarEntryKind.STATEMENT_DUE || entry.direction === TransferDirection.OUT;
}

export function signedAmountCents(entry: CalendarEntry): number {
  return isOutflow(entry) ? -entry.amountCents : entry.amountCents;
}
