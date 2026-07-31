package br.com.mkfinance.api.recurrence;

import br.com.mkfinance.domain.model.RecurrenceFrequency;
import br.com.mkfinance.domain.model.TransactionType;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

/** Contract schema {@code Recurrence}. */
public record RecurrenceResponse(
        UUID id,
        TransactionType type,
        UUID accountId,
        UUID categoryId,
        long amountCents,
        String description,
        String note,
        RecurrenceFrequency frequency,
        /** MONTHLY and YEARLY. A day the month does not have falls on its last day. */
        Integer dayOfMonth,
        /** WEEKLY only. */
        DayOfWeek dayOfWeek,
        /** YEARLY only, paired with {@code dayOfMonth}. */
        Integer monthOfYear,
        LocalDate startDate,
        /** Inclusive; null means open-ended. Set by a THIS_AND_FUTURE edit when it closes the rule. */
        LocalDate endDate,
        boolean active) {
}
