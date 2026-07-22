package br.com.mkfinance.api.transaction;

import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contract schema {@code TransactionRequest} — creating an INCOME/EXPENSE entry, or editing any
 * transaction.
 *
 * <p>{@code type} and {@code categoryId} are only bean-validated as "present" where the contract
 * requires them for creation; on a PATCH against a transfer leg they are ignored, because a
 * transfer has no category and its type cannot change. The service enforces the rest and answers
 * with machine codes (TRANSACTION_TYPE_IMMUTABLE, CATEGORY_TYPE_MISMATCH) rather than a bean
 * validation error, so the client can tell the cases apart.
 */
public record TransactionRequest(
        @NotNull UUID accountId,
        UUID categoryId,
        TransactionType type,
        @NotNull @Positive Long amountCents,
        @NotNull LocalDate date,
        @NotBlank @Size(max = 255) String description,
        String note,
        TransactionStatus status) {
}
