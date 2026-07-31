package br.com.mkfinance.application.calendar;

import br.com.mkfinance.domain.model.TransferDirection;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One row of the month agenda (contract schema {@code CalendarEntry}), before it is translated to
 * the api-layer response. Which fields are meaningful depends on {@link #kind()} — see
 * {@code api/calendar/CalendarEntryResponse} for the exact mapping.
 */
public record CalendarEntryView(
        LocalDate date,
        CalendarEntryKind kind,
        String description,
        long amountCents,
        TransferDirection direction,
        UUID transactionId,
        UUID statementId,
        UUID cardId,
        UUID accountId,
        UUID categoryId,
        UUID recurrenceId) {
}
