package br.com.mkfinance.tag;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.api.tag.TagRequest;
import br.com.mkfinance.api.tag.TagResponse;
import br.com.mkfinance.api.transaction.TransactionPageResponse;
import br.com.mkfinance.api.transaction.TransactionRequest;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.TransactionType;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** BE-8 aceite A — CRUD of tags (contract tag {@code tags}). */
class TagIntegrationTest extends AbstractTagIntegrationTest {

    @BeforeEach
    void setUp() {
        register();
    }

    @Nested
    @DisplayName("CRUD")
    class Crud {

        @Test
        void createsListsRenamesAndDeletesATag() {
            ResponseEntity<TagResponse> created =
                    session.post(path("/tags"), new TagRequest("Trabalho"), TagResponse.class);
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            TagResponse tag = created.getBody();
            assertThat(tag.name()).isEqualTo("Trabalho");

            TagResponse[] list = session.get(path("/tags"), TagResponse[].class).getBody();
            assertThat(list).extracting(TagResponse::id).contains(tag.id());

            ResponseEntity<TagResponse> renamed =
                    session.patch(path("/tags/" + tag.id()), new TagRequest("Trabalho remoto"), TagResponse.class);
            assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(renamed.getBody().name()).isEqualTo("Trabalho remoto");

            ResponseEntity<Void> deleted = session.delete(path("/tags/" + tag.id()), Void.class);
            assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(session.get(path("/tags"), TagResponse[].class).getBody())
                    .extracting(TagResponse::id)
                    .doesNotContain(tag.id());
        }

        @Test
        void listIsAlphabetical() {
            session.post(path("/tags"), new TagRequest("Zebra"), TagResponse.class);
            session.post(path("/tags"), new TagRequest("Abacaxi"), TagResponse.class);

            TagResponse[] list = session.get(path("/tags"), TagResponse[].class).getBody();
            assertThat(list).extracting(TagResponse::name).containsExactly("Abacaxi", "Zebra");
        }

        @Test
        void creatingATagThatOnlyDiffersByCaseIsAConflict() {
            session.post(path("/tags"), new TagRequest("Trabalho"), TagResponse.class);

            ResponseEntity<ErrorResponse> conflict =
                    session.post(path("/tags"), new TagRequest("trabalho"), ErrorResponse.class);

            assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(conflict.getBody().code()).isEqualTo("TAG_NAME_DUPLICATE");
        }

        @Test
        void renamingATagToAnotherTagsNameIsAConflict() {
            session.post(path("/tags"), new TagRequest("Trabalho"), TagResponse.class);
            TagResponse casa =
                    session.post(path("/tags"), new TagRequest("Casa"), TagResponse.class).getBody();

            ResponseEntity<ErrorResponse> conflict =
                    session.patch(path("/tags/" + casa.id()), new TagRequest("TRABALHO"), ErrorResponse.class);

            assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(conflict.getBody().code()).isEqualTo("TAG_NAME_DUPLICATE");
        }

        @Test
        void renamingATagToItsOwnNameWithDifferentCaseIsNotAConflict() {
            TagResponse tag = session.post(path("/tags"), new TagRequest("Trabalho"), TagResponse.class)
                    .getBody();

            ResponseEntity<TagResponse> renamed =
                    session.patch(path("/tags/" + tag.id()), new TagRequest("TRABALHO"), TagResponse.class);

            assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(renamed.getBody().name()).isEqualTo("TRABALHO");
        }

        @Test
        void updatingOrDeletingAnUnknownTagIs404() {
            UUID unknown = UUID.randomUUID();

            ResponseEntity<ErrorResponse> updateResponse =
                    session.patch(path("/tags/" + unknown), new TagRequest("Qualquer"), ErrorResponse.class);
            assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(updateResponse.getBody().code()).isEqualTo("TAG_NOT_FOUND");

            ResponseEntity<ErrorResponse> deleteResponse =
                    session.delete(path("/tags/" + unknown), ErrorResponse.class);
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(deleteResponse.getBody().code()).isEqualTo("TAG_NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("deleting a tag never touches the transactions carrying it")
    class DeleteUnlinksOnly {

        @Test
        void deletingATagRemovesItFromTheTransactionButKeepsTheTransaction() {
            AccountResponse account = createAccount("Conta", 0L);
            CategoryResponse category = someCategory(CategoryType.EXPENSE);
            TagResponse tag = session.post(path("/tags"), new TagRequest("Mercado"), TagResponse.class)
                    .getBody();

            TransactionResponse transaction = session.post(
                            path("/transactions"),
                            new TransactionRequest(
                                    account.id(),
                                    category.id(),
                                    TransactionType.EXPENSE,
                                    10_000L,
                                    LocalDate.of(2026, 8, 1),
                                    "Compra",
                                    null,
                                    null,
                                    List.of("Mercado")),
                            TransactionResponse.class)
                    .getBody();
            assertThat(transaction.tags()).extracting(TagResponse::name).containsExactly("Mercado");

            session.delete(path("/tags/" + tag.id()), Void.class);

            TransactionResponse[] page = session.get(path("/transactions"), TransactionPageResponse.class)
                    .getBody()
                    .content()
                    .toArray(TransactionResponse[]::new);
            TransactionResponse afterDelete = Arrays.stream(page)
                    .filter(t -> t.id().equals(transaction.id()))
                    .findFirst()
                    .orElseThrow();
            assertThat(afterDelete.tags()).isEmpty();
        }
    }
}
