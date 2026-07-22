package br.com.mkfinance.application.transaction;

import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service-layer command for an INCOME/EXPENSE entry, decoupled from the api-layer
 * {@code TransactionRequest} DTO.
 *
 * <p>When this updates an existing transfer leg, {@code type} and {@code categoryId} are ignored:
 * a transfer has no category and its type cannot change (TRANSACTION_TYPE_IMMUTABLE).
 */
public record TransactionInput(
        UUID accountId,
        UUID categoryId,
        TransactionType type,
        long amountCents,
        LocalDate date,
        String description,
        String note,
        TransactionStatus status) {
}
