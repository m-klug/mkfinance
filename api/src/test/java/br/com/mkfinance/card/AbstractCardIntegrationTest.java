package br.com.mkfinance.card;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.api.account.AccountRequest;
import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.auth.CurrentUserResponse;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.card.CardPurchaseRequest;
import br.com.mkfinance.api.card.CardPurchaseResponse;
import br.com.mkfinance.api.card.CardRequest;
import br.com.mkfinance.api.card.CardResponse;
import br.com.mkfinance.api.card.StatementResponse;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.support.ApiSession;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

/**
 * Shared fixture for BE-4's integration tests: a freshly registered user — hence its own workspace,
 * seeded categories and pillar allocation — plus the helpers each scenario builds on. Every test gets
 * an isolated workspace, so no test can see another's cards or statements.
 */
abstract class AbstractCardIntegrationTest extends AbstractIntegrationTest {

    protected ApiSession session;
    protected UUID workspaceId;

    protected void register() {
        session = newSession();
        session.primeCsrf();
        // Short fixed prefix rather than the test class name: an email's local part is capped at 64
        // characters, and "StatementPaymentIntegrationTest+<uuid>" overruns it.
        CurrentUserResponse user = session.post(
                        "/auth/register",
                        new RegisterRequest("Tester", "card+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"),
                        CurrentUserResponse.class)
                .getBody();
        workspaceId = user.workspaces().get(0).id();
    }

    protected String path(String suffix) {
        return "/workspaces/" + workspaceId + suffix;
    }

    protected AccountResponse createAccount(String name, long initialBalanceCents) {
        return session.post(
                        path("/accounts"),
                        new AccountRequest(name, AccountType.CHECKING, initialBalanceCents, null, null, null),
                        AccountResponse.class)
                .getBody();
    }

    protected CardResponse createCard(String name, long limitCents, int closingDay, int dueDay) {
        return createCard(name, limitCents, closingDay, dueDay, null);
    }

    protected CardResponse createCard(
            String name, long limitCents, int closingDay, int dueDay, UUID defaultPaymentAccountId) {
        return session.post(
                        path("/cards"),
                        new CardRequest(
                                name,
                                "VISA",
                                limitCents,
                                (short) closingDay,
                                (short) dueDay,
                                defaultPaymentAccountId,
                                null),
                        CardResponse.class)
                .getBody();
    }

    protected CardPurchaseResponse createPurchase(
            UUID cardId, String description, long totalAmountCents, LocalDate purchaseDate, int installmentCount) {
        return purchaseResponse(cardId, description, totalAmountCents, purchaseDate, installmentCount)
                .getBody();
    }

    protected ResponseEntity<CardPurchaseResponse> purchaseResponse(
            UUID cardId, String description, long totalAmountCents, LocalDate purchaseDate, int installmentCount) {
        return session.post(
                path("/cards/" + cardId + "/purchases"),
                new CardPurchaseRequest(
                        someExpenseCategory().id(),
                        description,
                        totalAmountCents,
                        purchaseDate,
                        (short) installmentCount),
                CardPurchaseResponse.class);
    }

    protected StatementResponse[] listStatements(UUID cardId, String query) {
        return session.get(path("/cards/" + cardId + "/statements" + query), StatementResponse[].class)
                .getBody();
    }

    protected StatementResponse statementOfMonth(UUID cardId, String month) {
        StatementResponse[] statements = listStatements(cardId, "?from=" + month + "&to=" + month);
        return statements[0];
    }

    /** Any seeded, non-system category of the given type — the seed always provides several. */
    protected CategoryResponse someCategory(CategoryType type) {
        CategoryResponse[] categories = session.get(path("/categories?type=" + type), CategoryResponse[].class)
                .getBody();
        return Arrays.stream(categories)
                .filter(category -> !category.system())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Workspace seed produced no " + type + " category"));
    }

    protected CategoryResponse someExpenseCategory() {
        return someCategory(CategoryType.EXPENSE);
    }
}
