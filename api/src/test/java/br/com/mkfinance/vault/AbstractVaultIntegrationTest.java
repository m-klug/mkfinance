package br.com.mkfinance.vault;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.api.account.AccountRequest;
import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.auth.CurrentUserResponse;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.vault.VaultRequest;
import br.com.mkfinance.api.vault.VaultResponse;
import br.com.mkfinance.application.vault.VaultLinkService;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.model.TransferDirection;
import br.com.mkfinance.domain.model.VaultEntrySource;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import br.com.mkfinance.support.ApiSession;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Shared fixture for BE-9's integration tests: a freshly registered user plus the account/vault
 * helpers every scenario needs. {@link #manualContribution} fabricates a transfer leg + its vault
 * link directly through {@link TransactionRepository}/{@link VaultLinkService} — BE-10's real
 * {@code TransferService}/{@code /transactions} endpoint does not exist yet on this branch (it is a
 * later ficha), so this is how BE-9 exercises the ledger end to end without depending on it.
 */
abstract class AbstractVaultIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    protected TransactionRepository transactionRepository;

    @Autowired
    protected VaultLinkService vaultLinkService;

    protected ApiSession session;
    protected UUID workspaceId;

    protected void register() {
        session = newSession();
        session.primeCsrf();
        // Short fixed prefix, not getClass().getSimpleName() — the email's local part is capped at
        // 64 chars (RFC 5321, enforced by Hibernate's @Email); see AbstractCalendarIntegrationTest.
        CurrentUserResponse user = session.post(
                        "/auth/register",
                        new RegisterRequest("Tester", "vault+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"),
                        CurrentUserResponse.class)
                .getBody();
        workspaceId = user.workspaces().get(0).id();
    }

    protected String path(String suffix) {
        return "/workspaces/" + workspaceId + suffix;
    }

    protected AccountResponse createAccount(String name, AccountType type, long initialBalanceCents) {
        return session.post(
                        path("/accounts"),
                        new AccountRequest(name, type, initialBalanceCents, null, null, null),
                        AccountResponse.class)
                .getBody();
    }

    protected VaultResponse createVault(UUID accountId, String name, ContributionPillar pillar) {
        return session.post(
                        path("/accounts/" + accountId + "/vaults"),
                        new VaultRequest(name, pillar, null, null, null),
                        VaultResponse.class)
                .getBody();
    }

    protected VaultResponse createVaultWithGoal(
            UUID accountId, String name, ContributionPillar pillar, long targetAmountCents, LocalDate targetDate) {
        return session.post(
                        path("/accounts/" + accountId + "/vaults"),
                        new VaultRequest(name, pillar, targetAmountCents, targetDate, null),
                        VaultResponse.class)
                .getBody();
    }

    /**
     * Fabricates a settled/pending "aporte" (contribution) or "resgate" (withdrawal) into a vault:
     * a lone TRANSFER leg (no sibling — the DB does not require one, {@code ck_transaction_pair})
     * plus its {@link VaultLinkService#link} mirror row, exactly what {@code TransferService} +
     * {@code VaultLinkService} will do once BE-10 lands.
     */
    protected Transaction manualContribution(
            UUID accountId, UUID vaultId, TransferDirection direction, long amountCents, TransactionStatus status) {
        Transaction leg = Transaction.builder()
                .workspaceId(workspaceId)
                .accountId(accountId)
                .type(TransactionType.TRANSFER)
                .transferDirection(direction)
                .amountCents(amountCents)
                .date(LocalDate.of(2026, 8, 5))
                .description(direction == TransferDirection.IN ? "Aporte" : "Resgate")
                .status(status)
                .build();
        leg = transactionRepository.save(leg);
        VaultEntrySource source =
                direction == TransferDirection.IN ? VaultEntrySource.CONTRIBUTION : VaultEntrySource.WITHDRAWAL;
        vaultLinkService.link(workspaceId, vaultId, leg.getId(), source);
        return leg;
    }
}
