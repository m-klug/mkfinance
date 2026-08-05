package br.com.mkfinance.api.vault;

import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.application.vault.VaultInput;
import br.com.mkfinance.application.vault.VaultLedgerLine;
import br.com.mkfinance.domain.model.Vault;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import br.com.mkfinance.domain.service.VaultBalanceCalculator.Composition;
import br.com.mkfinance.domain.service.VaultGoalCalculator;
import br.com.mkfinance.domain.service.VaultGoalCalculator.Goal;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VaultMapper {

    private final BusinessClock clock;

    public VaultInput toInput(VaultRequest request) {
        return new VaultInput(
                request.name(), request.contributionPillar(), request.targetAmountCents(), request.targetDate(),
                request.color());
    }

    /**
     * @param balance the vault's own balance (R5-equivalent, §4.1) — computed by
     *     {@code VaultBalanceService}, never read off the entity
     */
    public VaultResponse toResponse(Vault vault, Balance balance) {
        VaultGoalResponse goal = vault.hasGoal()
                ? toGoalResponse(VaultGoalCalculator.calculate(
                        vault.getTargetAmountCents(), vault.getTargetDate(), balance.currentCents(), clock.today()))
                : null;
        return new VaultResponse(
                vault.getId(),
                vault.getAccountId(),
                vault.getName(),
                vault.getContributionPillar(),
                vault.getTargetAmountCents(),
                vault.getTargetDate(),
                vault.getColor(),
                vault.isArchived(),
                balance.currentCents(),
                balance.projectedCents(),
                goal);
    }

    private VaultGoalResponse toGoalResponse(Goal goal) {
        return new VaultGoalResponse(
                goal.targetAmountCents(),
                goal.targetDate(),
                goal.remainingCents(),
                goal.progressPercent(),
                goal.monthsRemaining(),
                goal.monthlyNeededCents(),
                goal.status());
    }

    /**
     * The whole {@code listVaults} response (CT-2 decision 1) — every vault plus the two composition
     * totals, all computed server-side.
     *
     * @param vaults the account's vaults, already in the order the response should list them in
     * @param composition {@code VaultBalanceService.compositionOf}'s result for the same vaults
     */
    public VaultCompositionResponse toCompositionResponse(UUID accountId, List<Vault> vaults, Composition composition) {
        List<VaultResponse> vaultResponses = vaults.stream()
                .map(vault -> toResponse(
                        vault, composition.vaultBalances().getOrDefault(vault.getId(), new Balance(0L, 0L))))
                .toList();
        return new VaultCompositionResponse(
                accountId,
                composition.balanceCents(),
                composition.projectedBalanceCents(),
                composition.allocatedCents(),
                composition.unallocatedCents(),
                composition.projectedUnallocatedCents(),
                vaultResponses);
    }

    public VaultEntryResponse toEntryResponse(VaultLedgerLine line) {
        return new VaultEntryResponse(
                line.id(),
                line.vaultId(),
                line.date(),
                line.description(),
                line.amountCents(),
                line.source(),
                line.status(),
                line.transactionId(),
                line.reallocationId());
    }
}
