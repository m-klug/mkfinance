package br.com.mkfinance.application.account;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public List<Account> list(UUID workspaceId, boolean includeArchived) {
        return includeArchived
                ? accountRepository.findByWorkspaceIdOrderByNameAsc(workspaceId)
                : accountRepository.findByWorkspaceIdAndArchivedFalseOrderByNameAsc(workspaceId);
    }

    public Account create(UUID workspaceId, AccountInput input) {
        validateContributionPillar(input.type(), input.defaultContributionPillar());
        Account account = Account.builder()
                .workspaceId(workspaceId)
                .name(input.name())
                .type(input.type())
                .initialBalanceCents(input.initialBalanceCents())
                .defaultContributionPillar(input.defaultContributionPillar())
                .color(input.color())
                .icon(input.icon())
                .archived(false)
                .build();
        return accountRepository.save(account);
    }

    public Account update(UUID workspaceId, UUID accountId, AccountInput input) {
        validateContributionPillar(input.type(), input.defaultContributionPillar());
        Account account = getOwned(workspaceId, accountId);
        account.setName(input.name());
        account.setType(input.type());
        account.setInitialBalanceCents(input.initialBalanceCents());
        account.setDefaultContributionPillar(input.defaultContributionPillar());
        account.setColor(input.color());
        account.setIcon(input.icon());
        return accountRepository.save(account);
    }

    /** Idempotent — archiving an already-archived account just returns it. */
    public Account archive(UUID workspaceId, UUID accountId) {
        Account account = getOwned(workspaceId, accountId);
        account.setArchived(true);
        return accountRepository.save(account);
    }

    /** ACCOUNT_HAS_TRANSACTIONS if any transaction references it — archive instead (R contract). */
    public void delete(UUID workspaceId, UUID accountId) {
        Account account = getOwned(workspaceId, accountId);
        if (accountRepository.existsTransactionForAccount(account.getId())) {
            throw AppException.accountHasTransactions();
        }
        accountRepository.delete(account);
    }

    private Account getOwned(UUID workspaceId, UUID accountId) {
        return accountRepository.findByIdAndWorkspaceId(accountId, workspaceId)
                .orElseThrow(AppException::accountNotFound);
    }

    /** Only INVESTMENT/SAVINGS accounts may set a default contribution pillar (R9). */
    private void validateContributionPillar(AccountType type, Pillar pillar) {
        if (pillar == null) {
            return;
        }
        boolean allowedType = type == AccountType.INVESTMENT || type == AccountType.SAVINGS;
        boolean allowedPillar = pillar == Pillar.GOALS || pillar == Pillar.FINANCIAL_FREEDOM;
        if (!allowedType || !allowedPillar) {
            throw AppException.invalidContributionPillar();
        }
    }
}
