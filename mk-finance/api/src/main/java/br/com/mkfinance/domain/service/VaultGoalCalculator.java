package br.com.mkfinance.domain.service;

import br.com.mkfinance.domain.model.VaultGoalStatus;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * DESENVOLVIMENTO-CAIXINHAS.md §4.6 — a vault's goal progress and linear projection. Pure and
 * static (no Spring, no clock, no database), in the mold of {@link RecurrenceScheduler}: the API
 * hands the client a finished report, never raw {@code targetAmountCents}/current balance for it to
 * compute the same thing differently — the same precedent as
 * {@code PillarAllocationCalculator.Report}.
 *
 * <p>The projection is deliberately linear — it does not model returns. A fixed-income simulator
 * that would is a later phase (§9).
 */
public final class VaultGoalCalculator {

    private VaultGoalCalculator() {
    }

    /**
     * @param targetAmountCents the goal's amount, strictly positive
     * @param targetDate the deadline, or {@code null} for a goal with no date (valid — a value alone
     *     with nothing to project against)
     * @param remainingCents {@code max(0, target - current)}
     * @param progressPercent {@code current ÷ target × 100}, deliberately not capped at 100 — the
     *     client decides how to celebrate an overshoot, same as {@code actualPercent}
     * @param monthsRemaining counting the current competency, floored at zero; {@code null} only when
     *     there is no deadline
     * @param monthlyNeededCents {@code ceilDiv(remaining, monthsRemaining)}; zero once reached, the
     *     whole remainder once the deadline has passed (never null just because it is overdue —
     *     null only without a deadline)
     * @param status IN_PROGRESS, REACHED (wins over OVERDUE) or OVERDUE
     */
    public record Goal(
            long targetAmountCents,
            LocalDate targetDate,
            long remainingCents,
            double progressPercent,
            Integer monthsRemaining,
            Long monthlyNeededCents,
            VaultGoalStatus status) {
    }

    /**
     * @param targetAmountCents the vault's goal, strictly positive (DB check {@code target_amount_cents > 0})
     * @param targetDate the vault's deadline, or {@code null}
     * @param currentCents the vault's current (settled) balance — the goal tracks the same level the
     *     account balance itself does, never the projected one
     * @param today "today" in {@code America/Sao_Paulo} ({@code BusinessClock}), the caller's job to resolve
     */
    public static Goal calculate(long targetAmountCents, LocalDate targetDate, long currentCents, LocalDate today) {
        if (targetAmountCents <= 0) {
            throw new IllegalArgumentException("targetAmountCents must be positive, got " + targetAmountCents);
        }
        long remaining = Math.max(0L, targetAmountCents - currentCents);
        double progressPercent = (currentCents * 100.0) / targetAmountCents; // uncapped, deliberately
        boolean reached = remaining == 0L;

        if (targetDate == null) {
            // No deadline: nothing to project against, so monthsRemaining/monthlyNeeded stay null.
            return new Goal(targetAmountCents, null, remaining, progressPercent, null, null, reached
                    ? VaultGoalStatus.REACHED
                    : VaultGoalStatus.IN_PROGRESS);
        }

        int monthsRemaining = monthsRemainingFloored(today, targetDate);

        if (reached) {
            // Batida wins over vencida — a goal hit before the deadline stops asking, even past it.
            return new Goal(targetAmountCents, targetDate, 0L, progressPercent, monthsRemaining, 0L, VaultGoalStatus.REACHED);
        }

        if (monthsRemaining <= 0) {
            // Vencida: the deadline is behind us. The whole remainder is due now, never null.
            return new Goal(
                    targetAmountCents, targetDate, remaining, progressPercent, 0, remaining, VaultGoalStatus.OVERDUE);
        }

        // ceilDiv, not floorDiv: paying the floor every month would fall short by the remainder.
        long monthlyNeeded = Math.ceilDiv(remaining, monthsRemaining);
        return new Goal(
                targetAmountCents,
                targetDate,
                remaining,
                progressPercent,
                monthsRemaining,
                monthlyNeeded,
                VaultGoalStatus.IN_PROGRESS);
    }

    /** {@code MONTHS.between(YearMonth(today), YearMonth(targetDate)) + 1}, floored at zero (§4.6). */
    private static int monthsRemainingFloored(LocalDate today, LocalDate targetDate) {
        long months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(targetDate)) + 1;
        return (int) Math.max(0L, months);
    }
}
