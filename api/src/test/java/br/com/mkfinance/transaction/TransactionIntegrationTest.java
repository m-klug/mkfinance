package br.com.mkfinance.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.api.transaction.TransactionPageResponse;
import br.com.mkfinance.api.transaction.TransactionRequest;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.application.transaction.AccountBalanceService;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.service.BalanceCalculator.Balance;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** PLANEJAMENTO §6.3 — income/expense entries, one-click settle (R5) and the extrato filters. */
class TransactionIntegrationTest extends AbstractTransactionIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 5, 15);

    @Autowired
    private AccountBalanceService balanceService;

    private AccountResponse checking;
    private CategoryResponse expenseCategory;
    private CategoryResponse incomeCategory;

    @BeforeEach
    void setUp() {
        register();
        checking = createAccount("Conta Corrente", AccountType.CHECKING, 100_000L);
        expenseCategory = someCategory(CategoryType.EXPENSE);
        incomeCategory = someCategory(CategoryType.INCOME);
    }

    private TransactionResponse createExpense(long amountCents, LocalDate date, String description) {
        return session.post(
                        path("/transactions"),
                        new TransactionRequest(
                                checking.id(),
                                expenseCategory.id(),
                                TransactionType.EXPENSE,
                                amountCents,
                                date,
                                description,
                                null,
                                null, null),
                        TransactionResponse.class)
                .getBody();
    }

    private TransactionPageResponse listWith(String query) {
        return session.get(path("/transactions" + query), TransactionPageResponse.class)
                .getBody();
    }

    @Nested
    @DisplayName("entries")
    class Entries {

        @Test
        void createExpenseDefaultsToSettledAndDebitsTheAccount() {
            ResponseEntity<TransactionResponse> created = session.post(
                    path("/transactions"),
                    new TransactionRequest(
                            checking.id(),
                            expenseCategory.id(),
                            TransactionType.EXPENSE,
                            25_000L,
                            DATE,
                            "Mercado",
                            "compra do mês",
                            null, null),
                    TransactionResponse.class);

            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            TransactionResponse expense = created.getBody();
            assertThat(expense.status()).isEqualTo(TransactionStatus.SETTLED);
            assertThat(expense.transferPairId()).isNull();
            assertThat(expense.contributionPillar()).isNull();
            assertThat(balanceService.balanceOf(workspaceId, checking.id()).currentCents())
                    .isEqualTo(75_000L);
        }

        @Test
        void createIncomeCreditsTheAccount() {
            session.post(
                    path("/transactions"),
                    new TransactionRequest(
                            checking.id(),
                            incomeCategory.id(),
                            TransactionType.INCOME,
                            300_000L,
                            DATE,
                            "Salário",
                            null,
                            null, null),
                    TransactionResponse.class);

            assertThat(balanceService.balanceOf(workspaceId, checking.id()).currentCents())
                    .isEqualTo(400_000L);
        }

        @Test
        void updateChangesTheStoredEntry() {
            TransactionResponse expense = createExpense(25_000L, DATE, "Mercado");

            ResponseEntity<TransactionResponse> updated = session.patch(
                    path("/transactions/" + expense.id()),
                    new TransactionRequest(
                            checking.id(),
                            expenseCategory.id(),
                            TransactionType.EXPENSE,
                            31_500L,
                            DATE.plusDays(1),
                            "Mercado (corrigido)",
                            null,
                            null, null),
                    TransactionResponse.class);

            assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updated.getBody().amountCents()).isEqualTo(31_500L);
            assertThat(updated.getBody().description()).isEqualTo("Mercado (corrigido)");
            assertThat(balanceService.balanceOf(workspaceId, checking.id()).currentCents())
                    .isEqualTo(68_500L);
        }

        @Test
        void deleteRemovesTheEntryAndRestoresTheBalance() {
            TransactionResponse expense = createExpense(25_000L, DATE, "Mercado");

            ResponseEntity<Void> deleted = session.delete(path("/transactions/" + expense.id()), Void.class);

            assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(balanceService.balanceOf(workspaceId, checking.id()).currentCents())
                    .isEqualTo(100_000L);
        }

        @Test
        void anExpenseCannotBeFiledUnderAnIncomeCategory() {
            ResponseEntity<ErrorResponse> response = session.post(
                    path("/transactions"),
                    new TransactionRequest(
                            checking.id(),
                            incomeCategory.id(),
                            TransactionType.EXPENSE,
                            1_000L,
                            DATE,
                            "Categoria errada",
                            null,
                            null, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("CATEGORY_TYPE_MISMATCH");
        }

        @Test
        void transfersCannotBeCreatedThroughTheTransactionsEndpoint() {
            ResponseEntity<ErrorResponse> response = session.post(
                    path("/transactions"),
                    new TransactionRequest(
                            checking.id(),
                            expenseCategory.id(),
                            TransactionType.TRANSFER,
                            1_000L,
                            DATE,
                            "Transferência",
                            null,
                            null, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo("TRANSACTION_TYPE_IMMUTABLE");
        }

        @Test
        void anotherWorkspacesTransactionIsNotFound() {
            TransactionResponse mine = createExpense(1_000L, DATE, "Minha despesa");
            UUID foreignId = mine.id();
            register();

            // No GET-by-id in the contract; the settle route proves the scoping just as well.
            ResponseEntity<ErrorResponse> response =
                    session.post(path("/transactions/" + foreignId + "/settle"), null, ErrorResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo("TRANSACTION_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("settle (R5)")
    class Settle {

        @Test
        void pendingCountsOnlyTowardsTheProjectedBalanceUntilSettled() {
            TransactionResponse pending = session.post(
                            path("/transactions"),
                            new TransactionRequest(
                                    checking.id(),
                                    expenseCategory.id(),
                                    TransactionType.EXPENSE,
                                    40_000L,
                                    DATE,
                                    "Conta de luz",
                                    null,
                                    TransactionStatus.PENDING, null),
                            TransactionResponse.class)
                    .getBody();
            assertThat(pending.status()).isEqualTo(TransactionStatus.PENDING);

            Balance before = balanceService.balanceOf(workspaceId, checking.id());
            assertThat(before.currentCents()).isEqualTo(100_000L);
            assertThat(before.projectedCents()).isEqualTo(60_000L);

            ResponseEntity<TransactionResponse> settled =
                    session.post(path("/transactions/" + pending.id() + "/settle"), null, TransactionResponse.class);
            assertThat(settled.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(settled.getBody().status()).isEqualTo(TransactionStatus.SETTLED);

            Balance after = balanceService.balanceOf(workspaceId, checking.id());
            assertThat(after.currentCents()).isEqualTo(60_000L);
            assertThat(after.projectedCents()).isEqualTo(60_000L);
        }

        @Test
        void settlingTwiceIsAConflict() {
            TransactionResponse expense = createExpense(10_000L, DATE, "Já efetivada");

            ResponseEntity<ErrorResponse> response =
                    session.post(path("/transactions/" + expense.id() + "/settle"), null, ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo("TRANSACTION_ALREADY_SETTLED");
        }
    }

    @Nested
    @DisplayName("extrato filters")
    class Filters {

        @Test
        void filtersByPeriodAccountCategoryTypeAndText() {
            AccountResponse otherAccount = createAccount("Carteira", AccountType.CASH, 0L);
            CategoryResponse otherCategory = session.post(
                            path("/categories"),
                            new br.com.mkfinance.api.category.CategoryRequest(
                                    "Farmácia", CategoryType.EXPENSE, expenseCategory.pillar(), null, null, null),
                            CategoryResponse.class)
                    .getBody();

            createExpense(10_000L, LocalDate.of(2026, 1, 10), "Mercado janeiro");
            createExpense(20_000L, LocalDate.of(2026, 2, 10), "Mercado fevereiro");
            createExpense(30_000L, LocalDate.of(2026, 3, 10), "Padaria março");
            session.post(
                    path("/transactions"),
                    new TransactionRequest(
                            checking.id(),
                            incomeCategory.id(),
                            TransactionType.INCOME,
                            500_000L,
                            LocalDate.of(2026, 2, 5),
                            "Salário fevereiro",
                            null,
                            null, null),
                    TransactionResponse.class);
            session.post(
                    path("/transactions"),
                    new TransactionRequest(
                            otherAccount.id(),
                            otherCategory.id(),
                            TransactionType.EXPENSE,
                            4_000L,
                            LocalDate.of(2026, 2, 20),
                            "Remédio",
                            null,
                            null, null),
                    TransactionResponse.class);

            assertThat(listWith("").totalElements()).isEqualTo(5L);
            assertThat(listWith("?from=2026-02-01&to=2026-02-28").totalElements())
                    .as("period")
                    .isEqualTo(3L);
            assertThat(listWith("?accountId=" + otherAccount.id()).totalElements())
                    .as("account")
                    .isEqualTo(1L);
            assertThat(listWith("?categoryId=" + otherCategory.id()).totalElements())
                    .as("category")
                    .isEqualTo(1L);
            assertThat(listWith("?type=INCOME").totalElements()).as("type").isEqualTo(1L);
            assertThat(listWith("?search=mercado").totalElements())
                    .as("text search is case-insensitive")
                    .isEqualTo(2L);
            assertThat(listWith("?from=2026-02-01&to=2026-02-28&type=EXPENSE&accountId=" + checking.id())
                            .totalElements())
                    .as("filters combine")
                    .isEqualTo(1L);
        }

        @Test
        void filtersByStatus() {
            createExpense(10_000L, DATE, "Efetivada");
            session.post(
                    path("/transactions"),
                    new TransactionRequest(
                            checking.id(),
                            expenseCategory.id(),
                            TransactionType.EXPENSE,
                            20_000L,
                            DATE,
                            "Agendada",
                            null,
                            TransactionStatus.PENDING, null),
                    TransactionResponse.class);

            assertThat(listWith("?status=PENDING").totalElements()).isEqualTo(1L);
            assertThat(listWith("?status=SETTLED").totalElements()).isEqualTo(1L);
        }

        @Test
        void searchAlsoMatchesTheNote() {
            session.post(
                    path("/transactions"),
                    new TransactionRequest(
                            checking.id(),
                            expenseCategory.id(),
                            TransactionType.EXPENSE,
                            5_000L,
                            DATE,
                            "Compra",
                            "reembolsável pelo trabalho",
                            null, null),
                    TransactionResponse.class);

            assertThat(listWith("?search=reembols").totalElements()).isEqualTo(1L);
        }

        @Test
        void resultsArePaginatedNewestFirst() {
            for (int day = 1; day <= 5; day++) {
                createExpense(1_000L * day, LocalDate.of(2026, 4, day), "Dia " + day);
            }

            TransactionPageResponse firstPage = listWith("?page=0&size=2");
            assertThat(firstPage.totalElements()).isEqualTo(5L);
            assertThat(firstPage.page()).isZero();
            assertThat(firstPage.size()).isEqualTo(2);
            assertThat(firstPage.content()).extracting(TransactionResponse::description).containsExactly("Dia 5", "Dia 4");

            TransactionPageResponse secondPage = listWith("?page=1&size=2");
            assertThat(secondPage.content()).extracting(TransactionResponse::description).containsExactly("Dia 3", "Dia 2");

            TransactionPageResponse lastPage = listWith("?page=2&size=2");
            assertThat(lastPage.content()).extracting(TransactionResponse::description).containsExactly("Dia 1");
        }

        @Test
        void pageSizeIsCappedAtTheContractMaximum() {
            createExpense(1_000L, DATE, "Única");

            assertThat(listWith("?size=5000").size()).isEqualTo(200);
        }
    }
}
