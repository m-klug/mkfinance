package br.com.mkfinance.api.account;

import br.com.mkfinance.application.account.AccountInput;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

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
                account.isArchived());
    }
}
