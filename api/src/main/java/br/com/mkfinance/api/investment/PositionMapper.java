package br.com.mkfinance.api.investment;

import br.com.mkfinance.api.investment.PositionResponse.Estimate;
import br.com.mkfinance.api.investment.PositionResponse.Settlement;
import br.com.mkfinance.api.investment.PositionResponse.Valuation;
import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.application.investment.FixedIncomePositionInput;
import br.com.mkfinance.application.investment.FixedIncomePositionUpdate;
import br.com.mkfinance.application.investment.PositionView;
import br.com.mkfinance.application.investment.UnlinkedContribution;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.FixedIncomePosition;
import br.com.mkfinance.domain.model.PositionStatus;
import br.com.mkfinance.domain.model.Vault;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomePositionValuator;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import br.com.mkfinance.infrastructure.persistence.VaultRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Translates between the position contract and the application layer. Names of accounts and vaults
 * are looked up once per call, not once per row.
 */
@Component
@RequiredArgsConstructor
public class PositionMapper {

    private final AccountRepository accountRepository;
    private final VaultRepository vaultRepository;
    private final BusinessClock clock;

    public FixedIncomePositionInput toInput(PositionRequest request) {
        return new FixedIncomePositionInput(
                request.contributionTransactionId(),
                request.accountId(),
                request.principalCents(),
                request.purchaseDate(),
                request.vaultId(),
                request.name(),
                request.type(),
                request.indexer(),
                request.rate(),
                request.maturityDate());
    }

    public FixedIncomePositionUpdate toUpdate(PositionUpdateRequest request) {
        return new FixedIncomePositionUpdate(
                request.type(),
                request.indexer(),
                request.rate(),
                request.maturityDate(),
                request.name(),
                request.vaultId(),
                request.principalCents(),
                request.purchaseDate());
    }

    public PositionResponse toResponse(UUID workspaceId, PositionView view) {
        return toResponses(workspaceId, List.of(view)).get(0);
    }

    public List<PositionResponse> toResponses(UUID workspaceId, List<PositionView> views) {
        Names names = names(workspaceId);
        LocalDate today = clock.today();
        return views.stream().map(view -> response(view, names, today)).toList();
    }

    public List<UnlinkedContributionResponse> toUnlinked(UUID workspaceId, List<UnlinkedContribution> contributions) {
        Names names = names(workspaceId);
        return contributions.stream()
                .map(c -> new UnlinkedContributionResponse(
                        c.leg().getId(),
                        c.leg().getAccountId(),
                        names.accounts().get(c.leg().getAccountId()),
                        c.vaultId(),
                        c.vaultId() == null ? null : names.vaults().get(c.vaultId()),
                        c.leg().getAmountCents(),
                        c.leg().getDate(),
                        c.leg().getDescription()))
                .toList();
    }

    private record Names(Map<UUID, String> accounts, Map<UUID, String> vaults) {
    }

    private Names names(UUID workspaceId) {
        Map<UUID, String> accounts = accountRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .collect(Collectors.toMap(Account::getId, Account::getName));
        Map<UUID, String> vaults = vaultRepository.findByWorkspaceId(workspaceId).stream()
                .collect(Collectors.toMap(Vault::getId, Vault::getName));
        return new Names(accounts, vaults);
    }

    private PositionResponse response(PositionView view, Names names, LocalDate today) {
        FixedIncomePosition p = view.position();
        Settlement settlement = p.isSettled()
                ? new Settlement(p.getSettledDate(), p.getReceivedCents(), p.getEstimatedNetCents(), p.getYieldCents(),
                        p.getReturnTransferTransactionId())
                : null;
        boolean awaiting = p.getStatus() == PositionStatus.ACTIVE && !p.getMaturityDate().isAfter(today);
        return new PositionResponse(
                p.getId(),
                p.isManual() ? "MANUAL" : "CONTRIBUTION",
                p.getContributionTransactionId(),
                p.getAccountId(),
                names.accounts().get(p.getAccountId()),
                p.getVaultId(),
                p.getVaultId() == null ? null : names.vaults().get(p.getVaultId()),
                p.getName(),
                p.getType(),
                p.getIndexer(),
                p.getRate(),
                p.getPrincipalCents(),
                p.getPurchaseDate(),
                p.getMaturityDate(),
                p.getStatus(),
                awaiting,
                settlement,
                view.estimate() == null ? null : estimate(view.estimate()));
    }

    private static Estimate estimate(FixedIncomePositionValuator.Estimate estimate) {
        return new Estimate(valuation(estimate.today()), valuation(estimate.atMaturity()));
    }

    private static Valuation valuation(FixedIncomePositionValuator.Valuation v) {
        return new Valuation(v.asOf(), v.grossCents(), v.grossYieldCents(), v.iofCents(), v.incomeTaxCents(),
                v.custodyCents(), v.netCents(), v.netYieldCents(), v.lastIndexDate());
    }
}
