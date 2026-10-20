package br.com.mkfinance.api.transaction;

import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
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
 *
 * @param tags names, not ids (CT-1) — typing one that does not exist yet creates it. Sending the
 *     field replaces the whole set; omitting it (null) leaves the current tags alone.
 * @param reimbursableCents how much of an EXPENSE the user expects to get back (ADR-0002). Omitting
 *     it (null) leaves the current value alone; sending 0 clears it.
 */
public record TransactionRequest(
        @NotNull UUID accountId,
        UUID categoryId,
        TransactionType type,
        @NotNull @Positive Long amountCents,
        @NotNull LocalDate date,
        @NotBlank @Size(max = 255) String description,
        String note,
        TransactionStatus status,
        List<@Size(max = 40) String> tags,
        @PositiveOrZero Long reimbursableCents) {

    /** An entry that says nothing about reimbursement. */
    public TransactionRequest(
            UUID accountId,
            UUID categoryId,
            TransactionType type,
            Long amountCents,
            LocalDate date,
            String description,
            String note,
            TransactionStatus status,
            List<String> tags) {
        this(accountId, categoryId, type, amountCents, date, description, note, status, tags, null);
    }
}
