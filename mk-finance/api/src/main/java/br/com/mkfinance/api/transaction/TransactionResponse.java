package br.com.mkfinance.api.transaction;

import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import java.time.LocalDate;
import java.util.UUID;

/** Contract schema {@code Transaction}. */
public record TransactionResponse(
        UUID id,
        UUID accountId,
        /** Null for transfer legs. */
        UUID categoryId,
        TransactionType type,
        long amountCents,
        LocalDate date,
        String description,
        String note,
        TransactionStatus status,
        /** The other leg of the mirrored pair (R4); null for INCOME/EXPENSE. */
        UUID transferPairId,
        /** Set on transfers into INVESTMENT/SAVINGS accounts (R9). */
        ContributionPillar contributionPillar) {
}
