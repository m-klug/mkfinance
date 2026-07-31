package br.com.mkfinance.application.calendar;

/**
 * Contract schema {@code CalendarEntryKind}. Exactly two species (CT-1) — there is no separate
 * "recurrence occurrence" kind, because a materialized occurrence already <em>is</em> a
 * {@link #PENDING_TRANSACTION} (carrying {@code recurrenceId}); listing it again would double the
 * month.
 */
public enum CalendarEntryKind {
    STATEMENT_DUE,
    PENDING_TRANSACTION
}
