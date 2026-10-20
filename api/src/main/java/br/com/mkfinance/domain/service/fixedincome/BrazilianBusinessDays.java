package br.com.mkfinance.domain.service.fixedincome;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.HashSet;
import java.util.Set;

/**
 * Business-day calendar for Brazilian fixed income: weekends plus national holidays, the same set
 * the ANBIMA calendar uses. CDI and Selic accrue only on business days, 252 of them a year.
 *
 * <p>Holidays are computed, not stored: the fixed ones, plus the ones that hang off Easter
 * (Carnival Monday and Tuesday, Good Friday, Corpus Christi). Black Awareness Day (20 Nov) is a
 * national holiday from 2024 on. Pure and static: no Spring, no clock.
 */
public final class BrazilianBusinessDays {

    private BrazilianBusinessDays() {
    }

    public static boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !holidays(date.getYear()).contains(date);
    }

    /**
     * Business days in {@code [from, to)}: {@code from} counts, {@code to} does not. That is the
     * accrual window of an application made on {@code from} and redeemed on {@code to}.
     */
    public static int countBetween(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from: " + from + " > " + to);
        }
        int count = 0;
        for (LocalDate day = from; day.isBefore(to); day = day.plusDays(1)) {
            if (isBusinessDay(day)) {
                count++;
            }
        }
        return count;
    }

    static Set<LocalDate> holidays(int year) {
        Set<LocalDate> days = new HashSet<>();
        days.add(LocalDate.of(year, Month.JANUARY, 1));
        days.add(LocalDate.of(year, Month.APRIL, 21));
        days.add(LocalDate.of(year, Month.MAY, 1));
        days.add(LocalDate.of(year, Month.SEPTEMBER, 7));
        days.add(LocalDate.of(year, Month.OCTOBER, 12));
        days.add(LocalDate.of(year, Month.NOVEMBER, 2));
        days.add(LocalDate.of(year, Month.NOVEMBER, 15));
        if (year >= 2024) {
            days.add(LocalDate.of(year, Month.NOVEMBER, 20));
        }
        days.add(LocalDate.of(year, Month.DECEMBER, 25));

        LocalDate easter = easter(year);
        days.add(easter.minusDays(48)); // Carnival Monday
        days.add(easter.minusDays(47)); // Carnival Tuesday
        days.add(easter.minusDays(2)); // Good Friday
        days.add(easter.plusDays(60)); // Corpus Christi
        return days;
    }

    /** Easter Sunday by the anonymous Gregorian algorithm (Meeus/Jones/Butcher). */
    static LocalDate easter(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }
}
