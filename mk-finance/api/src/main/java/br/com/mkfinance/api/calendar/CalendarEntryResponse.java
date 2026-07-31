package br.com.mkfinance.api.calendar;

import br.com.mkfinance.application.calendar.CalendarEntryKind;
import br.com.mkfinance.domain.model.TransferDirection;
import java.time.LocalDate;
import java.util.UUID;

/** Contract schema {@code CalendarEntry}. */
public record CalendarEntryResponse(
        LocalDate date,
        CalendarEntryKind kind,
        /** The transaction's description, or the card's name for a statement due date. */
        String description,
        /** Always positive — the remaining balance of the statement for STATEMENT_DUE, the amount otherwise. */
        long amountCents,
        /** IN for money expected, OUT for money owed; null when {@code kind} carries it already. */
        TransferDirection direction,
        UUID transactionId,
        UUID statementId,
        UUID cardId,
        UUID accountId,
        UUID categoryId,
        /** Set when this pending transaction came from a recurrence. */
        UUID recurrenceId) {
}
