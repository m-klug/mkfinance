package br.com.mkfinance.api.transaction;

import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.model.TransferDirection;
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
        /**
         * Which side of the pair this leg is: OUT drains its account, IN feeds it. Null for
         * INCOME/EXPENSE. Since {@code amountCents} is always positive, this is the only thing
         * telling the two directions apart when a transfer is rendered in the extrato.
         */
        TransferDirection transferDirection,
        /** Set on transfers into INVESTMENT/SAVINGS accounts (R9). */
        ContributionPillar contributionPillar) {
}
