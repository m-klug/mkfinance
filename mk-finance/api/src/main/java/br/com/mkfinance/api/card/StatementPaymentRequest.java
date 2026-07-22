package br.com.mkfinance.api.card;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

/** Contract: {@code StatementPaymentRequest}. An amount below the total leaves the statement PARTIAL. */
public record StatementPaymentRequest(
        @NotNull UUID accountId, @NotNull @Positive Long amountCents, @NotNull LocalDate paymentDate) {
}
