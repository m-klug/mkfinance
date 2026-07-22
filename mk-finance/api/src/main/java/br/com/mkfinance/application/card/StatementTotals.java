package br.com.mkfinance.application.card;

import br.com.mkfinance.domain.model.StatementStatus;

/**
 * What a statement is worth right now: the sum of its installments and the sum of what has been paid
 * against it. Neither number is stored — both are derived, so they cannot drift out of sync with the
 * rows they summarize.
 *
 * @param totalCents sum of the installments allocated to the statement
 * @param paidCents sum of the payments registered against it
 */
public record StatementTotals(long totalCents, long paidCents) {

    public static final StatementTotals EMPTY = new StatementTotals(0L, 0L);

    /** What is still owed; never negative — the MVP does not model a credit balance. */
    public long remainingCents() {
        return Math.max(0L, totalCents - paidCents);
    }

    /**
     * The payment status these amounts imply — the value persisted on the statement. A statement
     * with nothing allocated to it stays OPEN even if that means "paid nothing of nothing".
     */
    public StatementStatus paymentStatus() {
        if (totalCents > 0 && paidCents >= totalCents) {
            return StatementStatus.PAID;
        }
        return paidCents > 0 ? StatementStatus.PARTIAL : StatementStatus.OPEN;
    }
}
