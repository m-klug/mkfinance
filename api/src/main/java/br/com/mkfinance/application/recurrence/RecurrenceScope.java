package br.com.mkfinance.application.recurrence;

/**
 * Which part of a series a scoped edit/delete reaches (contract schema {@code RecurrenceScope}).
 * Not persisted — a pure request-time concept, unlike {@code RecurrenceFrequency} which lives with
 * the entity in {@code domain.model}.
 */
public enum RecurrenceScope {
    /** Touches only the occurrence at {@code occurrenceDate}; the rule itself is left alone. */
    THIS_ONE,
    /** Closes the rule in force and starts a new one from {@code occurrenceDate}, keeping the past intact. */
    THIS_AND_FUTURE
}
