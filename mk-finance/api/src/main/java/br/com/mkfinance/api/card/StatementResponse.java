package br.com.mkfinance.api.card;

import br.com.mkfinance.domain.model.StatementStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contract: {@code Statement}.
 *
 * @param referenceMonth {@code YYYY-MM}; the month the statement closes in
 * @param status includes CLOSED, which is derived from the closing date rather than stored
 * @param totalCents sum of the installments allocated to it
 */
public record StatementResponse(
        UUID id,
        UUID cardId,
        String referenceMonth,
        LocalDate closingDate,
        LocalDate dueDate,
        StatementStatus status,
        long totalCents,
        long paidCents) {
}
