package br.com.mkfinance.application.transaction;

import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service-layer command for an INCOME/EXPENSE entry, decoupled from the api-layer
 * {@code TransactionRequest} DTO.
 *
 * <p>When {@code PATCH /transactions} addresses a transfer leg, {@code type} and {@code categoryId}
 * are dropped as the request is bridged to {@link TransferUpdateInput}: a transfer has no category
 * and its type cannot change (TRANSACTION_TYPE_IMMUTABLE). Re-targeting a contribution pillar is not
 * possible here — that goes through {@code PATCH /transfers}.
 *
 * @param tags names, not ids (CT-1) — {@code null} means "leave the current tags alone" on an edit
 *     (there is nothing to leave alone on a create, so it means "no tags" there); a present list,
 *     even empty, replaces the whole set.
 */
public record TransactionInput(
        UUID accountId,
        UUID categoryId,
        TransactionType type,
        long amountCents,
        LocalDate date,
        String description,
        String note,
        TransactionStatus status,
        List<String> tags) {
}
