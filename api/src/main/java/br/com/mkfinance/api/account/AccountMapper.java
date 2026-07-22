package br.com.mkfinance.api.account;

import br.com.mkfinance.application.account.AccountInput;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.Pillar;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountInput toInput(AccountRequest request) {
        long initialBalanceCents = request.initialBalanceCents() != null ? request.initialBalanceCents() : 0L;
        Pillar pillar = request.defaultContributionPillar() != null ? request.defaultContributionPillar().toPillar() : null;
        return new AccountInput(
                request.name(), request.type(), initialBalanceCents, pillar, request.color(), request.icon());
    }

    public AccountResponse toResponse(Account account) {
        ContributionPillar pillar = ContributionPillar.fromPillar(account.getDefaultContributionPillar());
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getInitialBalanceCents(),
                null,
                null,
                pillar,
                account.getColor(),
                account.getIcon(),
                account.isArchived());
    }
}
