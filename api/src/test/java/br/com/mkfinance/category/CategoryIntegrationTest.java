package br.com.mkfinance.category;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.api.account.AccountRequest;
import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.auth.CurrentUserResponse;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.category.CategoryRequest;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.support.ApiSession;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class CategoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiSession newRegisteredSession(UUID[] workspaceIdOut) {
        ApiSession session = newSession();
        session.primeCsrf();
        CurrentUserResponse user = session
                .post("/auth/register", new RegisterRequest("Tester", CategoryIntegrationTest.class.getSimpleName()
                        + "+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"), CurrentUserResponse.class)
                .getBody();
        workspaceIdOut[0] = user.workspaces().get(0).id();
        return session;
    }

    @Test
    void createListUpdateAndDeleteCategory() {
        UUID[] workspaceId = new UUID[1];
        ApiSession session = newRegisteredSession(workspaceId);
        String base = "/workspaces/" + workspaceId[0] + "/categories";

        ResponseEntity<CategoryResponse> created = session.post(
                base, new CategoryRequest("Pets", CategoryType.EXPENSE, Pillar.COMFORT, null, "#00FF00", "paw"), CategoryResponse.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        CategoryResponse category = created.getBody();
        assertThat(category.system()).isFalse();

        ResponseEntity<CategoryResponse[]> list = session.get(base, CategoryResponse[].class);
        assertThat(Arrays.stream(list.getBody()).map(CategoryResponse::id)).contains(category.id());

        ResponseEntity<CategoryResponse> updated = session.patch(
                base + "/" + category.id(),
                new CategoryRequest("Pets e Vet", CategoryType.EXPENSE, Pillar.COMFORT, null, "#00AA00", "paw"),
                CategoryResponse.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().name()).isEqualTo("Pets e Vet");

        ResponseEntity<Void> deleted = session.delete(base + "/" + category.id(), Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void expenseCategoryWithoutPillarIsRejected() {
        UUID[] workspaceId = new UUID[1];
        ApiSession session = newRegisteredSession(workspaceId);
        String base = "/workspaces/" + workspaceId[0] + "/categories";

        ResponseEntity<ErrorResponse> response = session.post(
                base, new CategoryRequest("Sem Pilar", CategoryType.EXPENSE, null, null, null, null), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("CATEGORY_PILLAR_REQUIRED");
    }

    @Test
    void incomeCategoryWithPillarIsRejected() {
        UUID[] workspaceId = new UUID[1];
        ApiSession session = newRegisteredSession(workspaceId);
        String base = "/workspaces/" + workspaceId[0] + "/categories";

        ResponseEntity<ErrorResponse> response = session.post(
                base, new CategoryRequest("Receita com Pilar", CategoryType.INCOME, Pillar.GOALS, null, null, null), ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("CATEGORY_PILLAR_FORBIDDEN");
    }

    @Test
    void deleteCategoryInUseIsBlocked() {
        UUID[] workspaceId = new UUID[1];
        ApiSession session = newRegisteredSession(workspaceId);
        String categoriesBase = "/workspaces/" + workspaceId[0] + "/categories";
        String accountsBase = "/workspaces/" + workspaceId[0] + "/accounts";

        CategoryResponse category = session
                .post(categoriesBase, new CategoryRequest("Em Uso", CategoryType.EXPENSE, Pillar.PLEASURES, null, null, null), CategoryResponse.class)
                .getBody();
        AccountResponse account = session
                .post(accountsBase, new AccountRequest("Conta", AccountType.CHECKING, 0L, null, null, null), AccountResponse.class)
                .getBody();

        // `transaction` belongs to BE-3 — insert directly to exercise the CATEGORY_IN_USE guard.
        jdbcTemplate.update(
                "insert into transaction (workspace_id, account_id, category_id, type, amount_cents, date, description, status) "
                        + "values (?, ?, ?, 'EXPENSE', 500, ?, 'seed', 'SETTLED')",
                workspaceId[0], account.id(), category.id(), LocalDate.now());

        ResponseEntity<ErrorResponse> response = session.delete(categoriesBase + "/" + category.id(), ErrorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("CATEGORY_IN_USE");
    }

    @Test
    void listCanBeFilteredByType() {
        UUID[] workspaceId = new UUID[1];
        ApiSession session = newRegisteredSession(workspaceId);

        ResponseEntity<CategoryResponse[]> incomeOnly =
                session.get("/workspaces/" + workspaceId[0] + "/categories?type=INCOME", CategoryResponse[].class);
        assertThat(incomeOnly.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(incomeOnly.getBody()).allMatch(c -> c.type() == CategoryType.INCOME);
        assertThat(incomeOnly.getBody()).allMatch(c -> c.pillar() == null);
    }
}
