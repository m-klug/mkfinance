package br.com.mkfinance.api.vault;

import br.com.mkfinance.domain.model.ContributionPillar;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Matches {@code components.schemas.Vault} in contracts/openapi.yaml. {@code balanceCents}/
 * {@code projectedBalanceCents} come from {@code VaultBalanceService} — never stored on the entity
 * (§4.1: "saldo nunca é materializado"). {@code goal} is present exactly when {@code targetAmountCents}
 * is set.
 */
public record VaultResponse(
        UUID id,
        UUID accountId,
        String name,
        ContributionPillar contributionPillar,
        Long targetAmountCents,
        LocalDate targetDate,
        String color,
        boolean archived,
        long balanceCents,
        long projectedBalanceCents,
        VaultGoalResponse goal) {
}
