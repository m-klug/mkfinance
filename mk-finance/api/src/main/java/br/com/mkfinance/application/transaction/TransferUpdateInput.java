package br.com.mkfinance.application.transaction;

import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.TransactionStatus;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service-layer command for editing an existing transfer (R4), symmetric to {@link TransferInput}
 * for creation. Kept apart from {@link TransactionInput} because a transfer has no category and its
 * type cannot change, so the INCOME/EXPENSE command has no business carrying transfer concepts.
 *
 * <p>Nullable fields mean "leave as is": {@code accountId} keeps the edited leg on its current
 * account, {@code status} keeps the current one, and {@code contributionPillar} keeps whichever
 * pillar the transfer already carries — an explicit value re-targets it (R9), and the engine refuses
 * a pillar aimed at a destination that takes no contribution. The shared facts (amount, date,
 * description) are required, since the engine writes them to both legs unconditionally.
 */
public record TransferUpdateInput(
        UUID accountId,
        long amountCents,
        LocalDate date,
        String description,
        String note,
        TransactionStatus status,
        ContributionPillar contributionPillar) {
}
