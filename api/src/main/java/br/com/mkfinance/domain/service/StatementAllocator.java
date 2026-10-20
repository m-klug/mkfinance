package br.com.mkfinance.domain.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * PLANEJAMENTO §5 R3 — decides which statement a purchase (and each of its installments) belongs to.
 *
 * <p>Two rules, and the whole credit-card feature hangs off them:
 *
 * <ol>
 *   <li>A purchase made <strong>before</strong> the closing day lands on the statement currently
 *       open; one made on the closing day or after lands on the next. The boundary is exclusive —
 *       the closing day already belongs to the new bill, which is how Banco Inter cuts it (a bill
 *       with closing day 3 holds purchases from the 3rd through the 2nd of the following month) and
 *       the case users notice when it is wrong.
 *   <li>Installments occupy <strong>consecutive</strong> reference months from there, so a 12×
 *       purchase simply walks forward twelve months, rolling over the year without any special
 *       casing.
 * </ol>
 *
 * <p>A statement's <em>reference month</em> is the month in which it closes. Its due date is the
 * first {@code dueDay} strictly after closing: when {@code dueDay > closingDay} that is still the
 * same month, otherwise it falls into the next one (closing on the 28th, due on the 5th → the 5th of
 * the following month).
 *
 * <p>Pure and static — dates in, dates out, no clock and no database. The contract caps both days at
 * 28 precisely so that no reference month can lack the day, February included; {@link #dayIn} still
 * clamps defensively so a hand-written row can never produce an invalid date.
 */
public final class StatementAllocator {

    private StatementAllocator() {
    }

    /** The closing/due dates a statement gets for a given reference month. */
    public record StatementSchedule(YearMonth referenceMonth, LocalDate closingDate, LocalDate dueDate) {
    }

    /**
     * The reference month of the statement a purchase falls into (R3).
     *
     * @param purchaseDate business date of the purchase (America/Sao_Paulo, R7)
     * @param closingDay day of month the card closes, 1–28
     */
    public static YearMonth firstReferenceMonth(LocalDate purchaseDate, int closingDay) {
        requireDayOfMonth(closingDay, "closingDay");
        YearMonth month = YearMonth.from(purchaseDate);
        // Exclusive: the closing day itself already opens the next statement.
        return purchaseDate.getDayOfMonth() < closingDay ? month : month.plusMonths(1);
    }

    /**
     * The reference months of every installment of a purchase, in order — consecutive months
     * starting at {@link #firstReferenceMonth}. Index 0 is installment number 1.
     */
    public static List<YearMonth> referenceMonths(LocalDate purchaseDate, int closingDay, int installmentCount) {
        if (installmentCount < 1) {
            throw new IllegalArgumentException("installmentCount must be at least 1, got " + installmentCount);
        }
        return consecutiveMonths(firstReferenceMonth(purchaseDate, closingDay), installmentCount);
    }

    /**
     * {@code count} consecutive reference months starting at {@code first}. Used directly when the
     * statement is dictated from outside — an imported bill says which statement a line belongs to,
     * whatever the purchase date would have computed.
     */
    public static List<YearMonth> consecutiveMonths(YearMonth first, int count) {
        List<YearMonth> months = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            months.add(first.plusMonths(i));
        }
        return months;
    }

    /**
     * The closing and due dates of the statement for {@code referenceMonth} — what
     * {@code application/card/StatementProvider} stores when it materializes the row.
     */
    public static StatementSchedule scheduleFor(YearMonth referenceMonth, int closingDay, int dueDay) {
        requireDayOfMonth(closingDay, "closingDay");
        requireDayOfMonth(dueDay, "dueDay");

        LocalDate closingDate = dayIn(referenceMonth, closingDay);
        // The bill is due at the first dueDay after closing — next month when it does not fit after
        // the closing day in the reference month itself (closing 28th, due 5th → 5th of next month).
        YearMonth dueMonth = dueDay > closingDay ? referenceMonth : referenceMonth.plusMonths(1);
        return new StatementSchedule(referenceMonth, closingDate, dayIn(dueMonth, dueDay));
    }

    /** {@code day} of {@code month}, clamped to the month's length so February can never overflow. */
    private static LocalDate dayIn(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    private static void requireDayOfMonth(int day, String name) {
        if (day < 1 || day > 28) {
            throw new IllegalArgumentException(name + " must be between 1 and 28, got " + day);
        }
    }
}
