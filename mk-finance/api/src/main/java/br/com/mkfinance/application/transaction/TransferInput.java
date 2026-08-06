package br.com.mkfinance.application.transaction;

import br.com.mkfinance.domain.model.ContributionPillar;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service-layer command for a transfer (R4). {@code contributionPillar} only applies when the
 * destination is an INVESTMENT/SAVINGS account (R9) and, when omitted, falls back to that
 * account's default pillar — unless the destination has an active vault, in which case the vault's
 * own pillar decides (DESENVOLVIMENTO-CAIXINHAS.md §4.4).
 *
 * <p>{@code fromVaultId}/{@code toVaultId} are only meaningful when the corresponding account has
 * at least one non-archived vault, in which case they are required, not optional (§4.4): the
 * source's for the withdrawal leg, the destination's for the contribution leg and its pillar.
 */
public record TransferInput(
        UUID fromAccountId,
        UUID toAccountId,
        long amountCents,
        LocalDate date,
        String description,
        ContributionPillar contributionPillar,
        UUID fromVaultId,
        UUID toVaultId) {
}
