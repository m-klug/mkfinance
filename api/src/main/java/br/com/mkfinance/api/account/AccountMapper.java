package br.com.mkfinance.api.account;

import br.com.mkfinance.application.account.AccountInput;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import br.com.mkfinance.infrastructure.persistence.VaultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@code hasVaults} (BE-9's exception to file ownership, DESENVOLVIMENTO-CAIXINHAS.md §5.4) is
 * resolved here — one existence query per account — rather than batched in
 * {@code AccountController}, since that controller belongs to BE-2 and this mapper's public
 * {@code toResponse(Account, Balance)} signature must stay exactly what it already calls.
 */
@Component
@RequiredArgsConstructor
public class AccountMapper {

    private final VaultRepository vaultRepository;

    public AccountInput toInput(AccountRequest request) {
        long initialBalanceCents = request.initialBalanceCents() != null ? request.initialBalanceCents() : 0L;
        Pillar pillar = request.defaultContributionPillar() != null ? request.defaultContributionPillar().toPillar() : null;
        return new AccountInput(
                request.name(), request.type(), initialBalanceCents, pillar, request.color(), request.icon());
    }

    /**
     * @param balance current and projected balance of this account (R5), computed by
     *     {@code AccountBalanceService}. Never null: an account with no history sits at its initial
     *     balance, which is a real answer, not a missing one.
     */
    public AccountResponse toResponse(Account account, Balance balance) {
        ContributionPillar pillar = ContributionPillar.fromPillar(account.getDefaultContributionPillar());
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getInitialBalanceCents(),
                balance.currentCents(),
                balance.projectedCents(),
                pillar,
                account.getColor(),
                account.getIcon(),
                account.isArchived(),
                vaultRepository.existsByAccountIdAndArchivedFalse(account.getId()));
    }
}
