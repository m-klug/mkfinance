package br.com.mkfinance.application.vault;

import br.com.mkfinance.domain.model.ContributionPillar;
import java.time.LocalDate;

/**
 * The application-layer shape of {@code VaultRequest} (CT-2) — {@code api/vault/VaultMapper}
 * translates between the two so {@code VaultService} never depends on the web DTO.
 */
public record VaultInput(
        String name, ContributionPillar contributionPillar, Long targetAmountCents, LocalDate targetDate, String color) {
}
