package br.com.mkfinance.api.transaction;

import br.com.mkfinance.api.tag.TagResponse;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.model.TransferDirection;
import java.time.LocalDate;
import java.util.List;
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
        ContributionPillar contributionPillar,
        /**
         * The vault this leg went into or came out of, when the account has vaults
         * (DESENVOLVIMENTO-CAIXINHAS.md §4.4); null for INCOME/EXPENSE and for a transfer leg on an
         * account with no vaults, exactly like today.
         */
        UUID vaultId,
        /** The rule that materialized this row, when it came from a recurrence (CT-1); null otherwise. */
        UUID recurrenceId,
        /**
         * Always sent, empty when the transaction has none (CT-1). Transfer legs are always empty —
         * {@code TransferRequest} has no {@code tags} field to set them with (BE-8 scope).
         */
        List<TagResponse> tags) {
}
