package br.com.mkfinance.domain.model;

/**
 * Lifecycle of a credit-card statement (contract: {@code StatementStatus}).
 *
 * <p>Only OPEN, PARTIAL and PAID are ever <em>stored</em> — they describe how much has been paid,
 * which only a payment changes. CLOSED is different in kind: it means "the closing date has passed",
 * a fact about the calendar rather than about the row, so it is derived at read time by
 * {@link Statement#effectiveStatus(java.time.LocalDate)}. Deriving it keeps the MVP free of a
 * scheduled job whose only purpose would be to flip statuses at midnight — and free of the bug where
 * that job did not run and a statement lies about being open.
 */
public enum StatementStatus {

    /** Still accumulating purchases: the closing date has not arrived and nothing was paid. */
    OPEN,

    /** Closed and awaiting payment (derived, never persisted). */
    CLOSED,

    /** Paid in full — immutable from here on (R6). */
    PAID,

    /** Partially paid; the remainder simply stays outstanding, with no revolving interest (§9.5). */
    PARTIAL
}
