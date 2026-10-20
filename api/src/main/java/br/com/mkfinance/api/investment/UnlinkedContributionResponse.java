package br.com.mkfinance.api.investment;

import java.time.LocalDate;
import java.util.UUID;

/** Contract schema {@code UnlinkedContribution}: a purchase no position claims yet. */
public record UnlinkedContributionResponse(
        UUID transactionId,
        UUID accountId,
        String accountName,
        UUID vaultId,
        String vaultName,
        long amountCents,
        LocalDate date,
        String description) {
}
