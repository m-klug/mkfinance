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
 * default pillar.
 */
public record TransferRequest(
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @NotNull @Positive Long amountCents,
        @NotNull LocalDate date,
        @NotBlank @Size(max = 255) String description,
        ContributionPillar contributionPillar) {
}
