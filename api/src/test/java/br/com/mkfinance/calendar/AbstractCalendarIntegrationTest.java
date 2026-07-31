package br.com.mkfinance.calendar;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.api.account.AccountRequest;
import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.auth.CurrentUserResponse;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.card.CardPurchaseRequest;
import br.com.mkfinance.api.card.CardPurchaseResponse;
import br.com.mkfinance.api.card.CardRequest;
import br.com.mkfinance.api.card.CardResponse;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.support.ApiSession;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;

/**
 * Shared fixture for BE-7's calendar integration tests: a freshly registered user (own workspace,
 * seeded categories) plus the account/card/category helpers each scenario needs.
 */
abstract class AbstractCalendarIntegrationTest extends AbstractIntegrationTest {

    protected ApiSession session;
    protected UUID workspaceId;

    protected void register() {
        session = newSession();
        session.primeCsrf();
        CurrentUserResponse user = session.post(
                        "/auth/register",
                        new RegisterRequest("Tester", "calendar+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"),
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
        return session.post(
                        path("/cards"),
                        new CardRequest(name, "VISA", limitCents, (short) closingDay, (short) dueDay, null, null),
                        CardResponse.class)
                .getBody();
    }

    protected CardPurchaseResponse createPurchase(
            UUID cardId, String description, long totalAmountCents, LocalDate purchaseDate, int installmentCount) {
        return session.post(
                        path("/cards/" + cardId + "/purchases"),
                        new CardPurchaseRequest(
                                someCategory(CategoryType.EXPENSE).id(),
                                description,
                                totalAmountCents,
                                purchaseDate,
                                (short) installmentCount),
                        CardPurchaseResponse.class)
                .getBody();
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
}
