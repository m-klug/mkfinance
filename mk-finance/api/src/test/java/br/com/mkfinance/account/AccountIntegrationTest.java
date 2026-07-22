package br.com.mkfinance.account;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.api.account.AccountRequest;
import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.auth.CurrentUserResponse;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.ContributionPillar;
import br.com.mkfinance.support.ApiSession;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class AccountIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiSession newRegisteredSession(UUID[] workspaceIdOut) {
        ApiSession session = newSession();
        session.primeCsrf();
        CurrentUserResponse user = session
                .post("/auth/register", new RegisterRequest("Tester", AccountIntegrationTest.class.getSimpleName()
                        + "+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"), CurrentUserResponse.class)
                .getBody();
        workspaceIdOut[0] = user.workspaces().get(0).id();
        return session;
    }

    @Test
    void createListUpdateAndArchiveAccount() {
        UUID[] workspaceId = new UUID[1];
        ApiSession session = newRegisteredSession(workspaceId);
        String base = "/workspaces/" + workspaceId[0] + "/accounts";

        ResponseEntity<AccountResponse> created = session.post(
                base,
                new AccountRequest("Conta Corrente", AccountType.CHECKING, 10_000L, null, "#123456", "wallet"),
                AccountResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AccountResponse account = created.getBody();
        assertThat(account.balanceCents()).isNull();
        assertThat(account.projectedBalanceCents()).isNull();
        assertThat(account.archived()).isFalse();

        ResponseEntity<AccountResponse[]> list = session.get(base, AccountResponse[].class);
        assertThat(list.getBody()).extracting(AccountResponse::id).contains(account.id());

        ResponseEntity<AccountResponse> updated = session.patch(
                base + "/" + account.id(),
                new AccountRequest("Conta Corrente Editada", AccountType.CHECKING, 20_000L, null, "#654321", "bank"),
                AccountResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().name()).isEqualTo("Conta Corrente Editada");
        assertThat(updated.getBody().initialBalanceCents()).isEqualTo(20_000L);

        ResponseEntity<AccountResponse> archived = session.post(base + "/" + account.id() + "/archive", null, AccountResponse.class);
        assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(archived.getBody().archived()).isTrue();

        // idempotent
        ResponseEntity<AccountResponse> archivedAgain = session.post(base + "/" + account.id() + "/archive", null, AccountResponse.class);
        assertThat(archivedAgain.getBody().archived()).isTrue();

        // hidden from the default (non-archived) listing, present with includeArchived=true
        ResponseEntity<AccountResponse[]> defaultList = session.get(base, AccountResponse[].class);
        assertThat(Arrays.stream(defaultList.getBody()).map(AccountResponse::id)).doesNotContain(account.id());
        ResponseEntity<AccountResponse[]> withArchived = session.get(base + "?includeArchived=true", AccountResponse[].class);
        assertThat(Arrays.stream(withArchived.getBody()).map(AccountResponse::id)).contains(account.id());
    }

    @Test
    void deleteAccountWithoutTransactionsSucceeds() {
        UUID[] workspaceId = new UUID[1];
        ApiSession session = newRegisteredSession(workspaceId);
        String base = "/workspaces/" + workspaceId[0] + "/accounts";

        AccountResponse account = session
                .post(base, new AccountRequest("Descartável", AccountType.CASH, 0L, null, null, null), AccountResponse.class)
                .getBody();

        ResponseEntity<Void> delete = session.delete(base + "/" + account.id(), Void.class);
        assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<AccountResponse[]> list = session.get(base + "?includeArchived=true", AccountResponse[].class);
        assertThat(Arrays.stream(list.getBody()).map(AccountResponse::id)).doesNotContain(account.id());
    }

    @Test
    void deleteAccountWithTransactionsIsBlocked() {
        UUID[] workspaceId = new UUID[1];
        ApiSession session = newRegisteredSession(workspaceId);
        String base = "/workspaces/" + workspaceId[0] + "/accounts";

        AccountResponse account = session
                .post(base, new AccountRequest("Com Movimentação", AccountType.CHECKING, 0L, null, null, null), AccountResponse.class)
                .getBody();

        CategoryResponse[] categories = session
                .get("/workspaces/" + workspaceId[0] + "/categories?type=EXPENSE", CategoryResponse[].class)
                .getBody();
        UUID categoryId = categories[0].id();

        // the `transaction` table belongs to BE-3 (no entity here yet) — insert directly to
        // prove the ACCOUNT_HAS_TRANSACTIONS guard works ahead of that workstream landing.
        jdbcTemplate.update(
                "insert into transaction (workspace_id, account_id, category_id, type, amount_cents, date, description, status) "
                        + "values (?, ?, ?, 'EXPENSE', 1500, ?, 'seed', 'SETTLED')",
                workspaceId[0], account.id(), categoryId, LocalDate.now());

        ResponseEntity<ErrorResponse> response = session.delete(base + "/" + account.id(), ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_HAS_TRANSACTIONS");

        // archive still works as the alternative
        ResponseEntity<AccountResponse> archived = session.post(base + "/" + account.id() + "/archive", null, AccountResponse.class);
        assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void defaultContributionPillarOnlyAllowedForInvestmentOrSavingsWithGoalsOrFinancialFreedom() {
        UUID[] workspaceId = new UUID[1];
        ApiSession session = newRegisteredSession(workspaceId);
        String base = "/workspaces/" + workspaceId[0] + "/accounts";

        // CHECKING + a contribution pillar -> rejected
        ResponseEntity<ErrorResponse> rejected = session.post(
                base,
                new AccountRequest("Conta Corrente", AccountType.CHECKING, 0L, ContributionPillar.GOALS, null, null),
                ErrorResponse.class);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().code()).isEqualTo("INVALID_CONTRIBUTION_PILLAR");

        // INVESTMENT + GOALS -> accepted
        ResponseEntity<AccountResponse> accepted = session.post(
                base,
                new AccountRequest("Corretora", AccountType.INVESTMENT, 0L, ContributionPillar.GOALS, null, null),
                AccountResponse.class);
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(accepted.getBody().defaultContributionPillar()).isEqualTo(ContributionPillar.GOALS);
    }
}
