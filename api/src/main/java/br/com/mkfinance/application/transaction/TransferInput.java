package br.com.mkfinance.application.transaction;

import br.com.mkfinance.domain.model.ContributionPillar;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service-layer command for a transfer (R4). {@code contributionPillar} only applies when the
 * destination is an INVESTMENT/SAVINGS account (R9) and, when omitted, falls back to that
 * account's default pillar.
 */
public record TransferInput(
        UUID fromAccountId,
        UUID toAccountId,
        long amountCents,
        LocalDate date,
        String description,
        ContributionPillar contributionPillar) {
}
