package br.com.mkfinance.api.transaction;

import br.com.mkfinance.domain.model.ContributionPillar;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contract schema {@code TransferRequest}. {@code contributionPillar} is only accepted when the
 * destination is an INVESTMENT/SAVINGS account (R9); left null, it falls back to that account's
 * default pillar — unless the destination has an active vault, in which case the vault's own pillar
 * decides and a disagreeing value answers VAULT_PILLAR_CONFLICT (DESENVOLVIMENTO-CAIXINHAS.md §4.4).
 *
 * <p>{@code fromVaultId}/{@code toVaultId}: required (VAULT_REQUIRED) once the source/destination
 * account has a non-archived vault, refused (VAULT_NOT_FOUND) when it has none.
 */
public record TransferRequest(
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @NotNull @Positive Long amountCents,
        @NotNull LocalDate date,
        @NotBlank @Size(max = 255) String description,
        ContributionPillar contributionPillar,
        UUID fromVaultId,
        UUID toVaultId) {
}
