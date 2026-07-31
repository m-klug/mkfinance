package br.com.mkfinance.domain.service;

import br.com.mkfinance.domain.model.RecurrenceFrequency;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * PLANEJAMENTO §7 (Fase 2) — turns a recurrence rule's explicit schedule into the calendar dates it
 * falls on within a single competency (month).
 *
 * <p>The schedule is explicit — {@code dayOfMonth}, {@code dayOfWeek}, {@code monthOfYear} — never
 * derived from {@code startDate} (CT-1): changing which day the rent is due must not move the date
 * the series started on.
 *
 * <p>A MONTHLY/YEARLY day the month does not have falls on the month's last day instead of skipping
 * it — 31 becomes the 28th in February, never nothing (same rule {@link StatementAllocator} applies
 * to statement closing/due days). Pure and static: dates in, dates out, no clock and no database.
 * Callers pass {@code startDate}/{@code endDate} so the same call both generates and bounds the
 * occurrences in one pass — {@code endDate == null} means open-ended.
 */
public final class RecurrenceScheduler {

    private RecurrenceScheduler() {
    }

    /**
     * The occurrence dates of a rule that fall in {@code month}, already bounded by
     * {@code [startDate, endDate]} ({@code endDate} null = open-ended).
     *
     * @param dayOfMonth required for MONTHLY and YEARLY, 1-31
     * @param dayOfWeek required for WEEKLY
     * @param monthOfYear required for YEARLY, 1-12, paired with {@code dayOfMonth}
     */
    public static List<LocalDate> occurrencesIn(
            RecurrenceFrequency frequency,
            Integer dayOfMonth,
            DayOfWeek dayOfWeek,
            Integer monthOfYear,
            LocalDate startDate,
            LocalDate endDate,
            YearMonth month) {
        List<LocalDate> raw = switch (frequency) {
            case MONTHLY -> monthly(dayOfMonth, month);
            case WEEKLY -> weekly(dayOfWeek, month);
            case YEARLY -> yearly(dayOfMonth, monthOfYear, month);
        };

        List<LocalDate> bounded = new ArrayList<>(raw.size());
        for (LocalDate date : raw) {
            if (date.isBefore(startDate)) {
                continue;
            }
            if (endDate != null && date.isAfter(endDate)) {
                continue;
            }
            bounded.add(date);
        }
        return bounded;
    }

    private static List<LocalDate> monthly(Integer dayOfMonth, YearMonth month) {
        requireDayOfMonth(dayOfMonth);
        return List.of(dayIn(month, dayOfMonth));
    }

    private static List<LocalDate> weekly(DayOfWeek dayOfWeek, YearMonth month) {
        if (dayOfWeek == null) {
            throw new IllegalArgumentException("dayOfWeek is required for WEEKLY");
        }
        List<LocalDate> dates = new ArrayList<>();
        LocalDate lastDay = month.atEndOfMonth();
        LocalDate date = month.atDay(1).with(TemporalAdjusters.nextOrSame(dayOfWeek));
        while (!date.isAfter(lastDay)) {
            dates.add(date);
            date = date.plusWeeks(1);
        }
        return dates;
    }

    private static List<LocalDate> yearly(Integer dayOfMonth, Integer monthOfYear, YearMonth month) {
        requireDayOfMonth(dayOfMonth);
        if (monthOfYear == null || monthOfYear < 1 || monthOfYear > 12) {
            throw new IllegalArgumentException("monthOfYear must be between 1 and 12 for YEARLY");
        }
        if (month.getMonthValue() != monthOfYear) {
            return List.of();
        }
        return List.of(dayIn(month, dayOfMonth));
    }

    /** {@code day} of {@code month}, clamped to the month's length so February can never overflow. */
    private static LocalDate dayIn(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    private static void requireDayOfMonth(Integer dayOfMonth) {
        if (dayOfMonth == null || dayOfMonth < 1 || dayOfMonth > 31) {
            throw new IllegalArgumentException("dayOfMonth must be between 1 and 31, got " + dayOfMonth);
        }
    }
}
