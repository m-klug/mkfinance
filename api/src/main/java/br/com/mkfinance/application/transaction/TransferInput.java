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
 *
 * <p>{@code externalId} is the bank's id for the movement (OFX FITID) and belongs to exactly one
 * leg: the one on {@code externalIdAccountId}, the account the statement was imported into. The
 * other leg has no bank record of its own in that file.
 */
public record TransferInput(
        UUID fromAccountId,
        UUID toAccountId,
        long amountCents,
        LocalDate date,
        String description,
        ContributionPillar contributionPillar,
        UUID fromVaultId,
        UUID toVaultId,
        String externalId,
        UUID externalIdAccountId) {

    /** A transfer that did not come from a bank import. */
    public TransferInput(
            UUID fromAccountId,
            UUID toAccountId,
            long amountCents,
            LocalDate date,
            String description,
            ContributionPillar contributionPillar,
            UUID fromVaultId,
            UUID toVaultId) {
        this(fromAccountId, toAccountId, amountCents, date, description, contributionPillar, fromVaultId, toVaultId, null, null);
    }
}
