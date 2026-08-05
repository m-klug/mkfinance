package br.com.mkfinance.transaction;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.api.account.AccountRequest;
import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.auth.CurrentUserResponse;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.support.ApiSession;
import java.util.Arrays;
import java.util.UUID;

/**
 * Shared fixture for BE-3's integration tests: a freshly registered user (hence its own workspace,
 * seeded categories and pillar allocation) plus helpers to create the accounts each scenario needs.
 * Every test gets an isolated workspace, so tests never see each other's data.
 */
abstract class AbstractTransactionIntegrationTest extends AbstractIntegrationTest {

    protected ApiSession session;
    protected UUID workspaceId;

    protected void register() {
        session = newSession();
        session.primeCsrf();
        // A short, fixed prefix rather than getClass().getSimpleName(): the email's local part is
        // capped at 64 chars (RFC 5321, enforced by Hibernate's @Email), and a longer test class name
        // plus the UUID can silently exceed it — see AbstractCalendarIntegrationTest/AbstractCardIntegrationTest
        // for the same convention.
        CurrentUserResponse user = session.post(
                        "/auth/register",
                        new RegisterRequest("Tester", "tx+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"),
                        CurrentUserResponse.class)
                .getBody();
        workspaceId = user.workspaces().get(0).id();
    }

    protected String path(String suffix) {
        return "/workspaces/" + workspaceId + suffix;
    }

    protected AccountResponse createAccount(String name, AccountType type, long initialBalanceCents) {
        return createAccount(name, type, initialBalanceCents, null);
    }

    protected AccountResponse createAccount(
            String name, AccountType type, long initialBalanceCents, ContributionPillar defaultPillar) {
        return session.post(
                        path("/accounts"),
                        new AccountRequest(name, type, initialBalanceCents, defaultPillar, null, null),
                        AccountResponse.class)
                .getBody();
    }

    /** Any seeded category of the given type — the seed always provides several of each. */
    protected CategoryResponse someCategory(CategoryType type) {
        CategoryResponse[] categories = session.get(path("/categories?type=" + type), CategoryResponse[].class)
                .getBody();
        return Arrays.stream(categories)
                .filter(category -> !category.system())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Workspace seed produced no " + type + " category"));
    }
}
