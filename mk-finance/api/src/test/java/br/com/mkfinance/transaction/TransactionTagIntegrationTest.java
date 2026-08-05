package br.com.mkfinance.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.api.tag.TagResponse;
import br.com.mkfinance.api.transaction.TransactionPageResponse;
import br.com.mkfinance.api.transaction.TransactionRequest;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.TransactionType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * BE-8 — {@code TransactionRequest.tags}/{@code Transaction.tags} (CT-1) and the extrato's {@code
 * tags} OR filter.
 */
class TransactionTagIntegrationTest extends AbstractTransactionIntegrationTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 10);

    private AccountResponse account;
    private CategoryResponse category;

    @BeforeEach
    void setUp() {
        register();
        account = createAccount("Conta", AccountType.CHECKING, 0L);
        category = someCategory(CategoryType.EXPENSE);
    }

    private TransactionResponse createWithTags(List<String> tags) {
        return session.post(
                        path("/transactions"),
                        new TransactionRequest(
                                account.id(), category.id(), TransactionType.EXPENSE, 5_000L, DATE, "Compra", null, null, tags),
                        TransactionResponse.class)
                .getBody();
    }

    @Nested
    @DisplayName("assigning tags")
    class Assigning {

        @Test
        void creatingATransactionCreatesMissingTagsAndAttachesThem() {
            TransactionResponse transaction = createWithTags(List.of("Viagem", "Trabalho"));

            assertThat(transaction.tags()).extracting(TagResponse::name).containsExactlyInAnyOrder("Viagem", "Trabalho");
            TagResponse[] workspaceTags = session.get(path("/tags"), TagResponse[].class).getBody();
            assertThat(workspaceTags).extracting(TagResponse::name).contains("Viagem", "Trabalho");
        }

        @Test
        void duplicateTagNamesInTheSameRequestCollapseCaseInsensitively() {
            TransactionResponse transaction = createWithTags(List.of("Viagem", "viagem", "VIAGEM"));

            assertThat(transaction.tags()).hasSize(1);
            assertThat(transaction.tags().get(0).name()).isEqualTo("Viagem");
        }

        @Test
        void omittingTagsCreatesATransactionWithNoTags() {
            TransactionResponse transaction = createWithTags(null);

            assertThat(transaction.tags()).isNotNull().isEmpty();
        }

        @Test
        void updatingReplacesTheWholeSet() {
            TransactionResponse transaction = createWithTags(List.of("Viagem"));

            ResponseEntity<TransactionResponse> updated = session.patch(
                    path("/transactions/" + transaction.id()),
                    new TransactionRequest(
                            account.id(),
                            category.id(),
                            TransactionType.EXPENSE,
                            5_000L,
                            DATE,
                            "Compra",
                            null,
                            null,
                            List.of("Trabalho")),
                    TransactionResponse.class);

            assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(updated.getBody().tags()).extracting(TagResponse::name).containsExactly("Trabalho");
        }

        @Test
        void omittingTagsOnUpdateLeavesTheCurrentTagsAlone() {
            TransactionResponse transaction = createWithTags(List.of("Viagem"));

            ResponseEntity<TransactionResponse> updated = session.patch(
                    path("/transactions/" + transaction.id()),
                    new TransactionRequest(
                            account.id(),
                            category.id(),
                            TransactionType.EXPENSE,
                            7_000L,
                            DATE,
                            "Compra corrigida",
                            null,
                            null,
                            null),
                    TransactionResponse.class);

            assertThat(updated.getBody().amountCents()).isEqualTo(7_000L);
            assertThat(updated.getBody().tags()).extracting(TagResponse::name).containsExactly("Viagem");
        }

        @Test
        void sendingAnEmptyListClearsTheTags() {
            TransactionResponse transaction = createWithTags(List.of("Viagem"));

            ResponseEntity<TransactionResponse> updated = session.patch(
                    path("/transactions/" + transaction.id()),
                    new TransactionRequest(
                            account.id(), category.id(), TransactionType.EXPENSE, 5_000L, DATE, "Compra", null, null, List.of()),
                    TransactionResponse.class);

            assertThat(updated.getBody().tags()).isEmpty();
        }

    }

    @Nested
    @DisplayName("extrato filter (OR, case-insensitive)")
    class Filtering {

        @Test
        void filteringByTwoTagsReturnsTheUnion() {
            createWithTags(List.of("Viagem"));
            createWithTags(List.of("Trabalho"));
            createWithTags(List.of("Mercado"));

            TransactionPageResponse result = session.get(
                            path("/transactions?tags=Viagem&tags=Trabalho"), TransactionPageResponse.class)
                    .getBody();

            assertThat(result.totalElements()).isEqualTo(2L);
        }

        @Test
        void filterIsCaseInsensitive() {
            createWithTags(List.of("Viagem"));

            TransactionPageResponse result =
                    session.get(path("/transactions?tags=VIAGEM"), TransactionPageResponse.class).getBody();

            assertThat(result.totalElements()).isEqualTo(1L);
        }

        @Test
        void anUnknownTagNameMatchesNothing() {
            createWithTags(List.of("Viagem"));

            TransactionPageResponse result =
                    session.get(path("/transactions?tags=Inexistente"), TransactionPageResponse.class).getBody();

            assertThat(result.totalElements()).isZero();
        }

        @Test
        void combinesWithOtherFilters() {
            createWithTags(List.of("Viagem"));
            session.post(
                    path("/transactions"),
                    new TransactionRequest(
                            account.id(),
                            category.id(),
                            TransactionType.EXPENSE,
                            9_000L,
                            DATE.plusDays(1),
                            "Outra data",
                            null,
                            null,
                            List.of("Viagem")),
                    TransactionResponse.class);

            TransactionPageResponse result = session.get(
                            path("/transactions?tags=Viagem&from=" + DATE + "&to=" + DATE), TransactionPageResponse.class)
                    .getBody();

            assertThat(result.totalElements()).isEqualTo(1L);
        }
    }
}
