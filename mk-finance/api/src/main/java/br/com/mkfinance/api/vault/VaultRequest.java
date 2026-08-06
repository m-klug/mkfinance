package br.com.mkfinance.api.vault;

import br.com.mkfinance.domain.model.ContributionPillar;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** Matches {@code components.schemas.VaultRequest} in contracts/openapi.yaml. */
public record VaultRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull ContributionPillar contributionPillar,
        @Positive Long targetAmountCents,
        LocalDate targetDate,
        String color) {
}
