package br.com.mkfinance.api.vault;

import br.com.mkfinance.domain.model.VaultGoalStatus;
import java.time.LocalDate;

/** Matches {@code components.schemas.VaultGoal} in contracts/openapi.yaml. */
public record VaultGoalResponse(
        long targetAmountCents,
        LocalDate targetDate,
        long remainingCents,
        double progressPercent,
        Integer monthsRemaining,
        Long monthlyNeededCents,
        VaultGoalStatus status) {
}
