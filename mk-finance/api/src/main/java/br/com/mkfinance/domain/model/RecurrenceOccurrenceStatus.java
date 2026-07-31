package br.com.mkfinance.domain.model;

/**
 * The state of a single {@link RecurrenceOccurrence}. {@code GENERATED} means a PENDING transaction
 * was materialized for it; {@code SKIPPED} means the user chose "pular esta ocorrência" — recorded
 * even when nothing was ever materialized, so the lazy materializer never recreates it.
 */
public enum RecurrenceOccurrenceStatus {
    GENERATED,
    SKIPPED
}
