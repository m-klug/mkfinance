package br.com.mkfinance.application.recurrence;

import br.com.mkfinance.domain.model.RecurrenceFrequency;
import br.com.mkfinance.domain.model.TransactionType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service-layer command for {@code RecurrenceRuleService}, decoupled from the api-layer
 * {@code RecurrenceRequest} DTO — mirrors {@code application/transaction/TransactionInput}.
 */
public record RecurrenceRuleInput(
        TransactionType type,
        UUID accountId,
        UUID categoryId,
        long amountCents,
        String description,
        String note,
        RecurrenceFrequency frequency,
        Integer dayOfMonth,
        DayOfWeek dayOfWeek,
        Integer monthOfYear,
        LocalDate startDate,
        LocalDate endDate,
        Boolean active) {
}
