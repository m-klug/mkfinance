package br.com.mkfinance.api.recurrence;

import br.com.mkfinance.domain.model.RecurrenceFrequency;
import br.com.mkfinance.domain.model.TransactionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contract schema {@code RecurrenceRequest}. The scheduling fields required depend on
 * {@code frequency} — {@code dayOfMonth} for MONTHLY, {@code dayOfWeek} for WEEKLY,
 * {@code dayOfMonth} + {@code monthOfYear} for YEARLY. OpenAPI cannot express that conditional, so
 * {@code RecurrenceRuleService} checks it and answers VALIDATION_ERROR with the offending field(s)
 * (CT-1).
 *
 * <p>For a {@code THIS_ONE}-scoped PATCH the scheduling fields (including {@code frequency} and
 * {@code startDate}) are ignored entirely — one occurrence has no schedule of its own — but the
 * contract still requires them present on the wire, since it is the same request shape for every
 * scope and endpoint.
 */
public record RecurrenceRequest(
        @NotNull TransactionType type,
        @NotNull UUID accountId,
        @NotNull UUID categoryId,
        @NotNull @Positive Long amountCents,
        @NotBlank @Size(max = 255) String description,
        String note,
        @NotNull RecurrenceFrequency frequency,
        @Min(1) @Max(31) Integer dayOfMonth,
        DayOfWeek dayOfWeek,
        @Min(1) @Max(12) Integer monthOfYear,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        Boolean active) {
}
