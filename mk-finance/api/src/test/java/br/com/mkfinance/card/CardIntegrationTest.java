package br.com.mkfinance.card;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.card.CardPurchaseRequest;
import br.com.mkfinance.api.card.CardRequest;
import br.com.mkfinance.api.card.CardResponse;
import br.com.mkfinance.api.card.StatementPaymentRequest;
import br.com.mkfinance.api.card.StatementResponse;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.domain.exception.ErrorCode;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Card CRUD, the available-limit calculation, and lazy statement materialization. */
class CardIntegrationTest extends AbstractCardIntegrationTest {

    private AccountResponse account;

    @BeforeEach
    void setUp() {
        register();
        account = createAccount("Conta corrente", 500_000L);
    }

    @Nested
    @DisplayName("CRUD")
    class Crud {

        @Test
        void createsAndListsACard() {
            CardResponse created = createCard("Nubank", 1_000_000L, 20, 5, account.id());

            assertThat(created.id()).isNotNull();
            assertThat(created.closingDay()).isEqualTo((short) 20);
            assertThat(created.dueDay()).isEqualTo((short) 5);
            assertThat(created.archived()).isFalse();
            // Nothing charged yet, so the whole limit is available.
            assertThat(created.availableLimitCents()).isEqualTo(1_000_000L);

            CardResponse[] cards = session.get(path("/cards"), CardResponse[].class).getBody();
            assertThat(cards).extracting(CardResponse::name).containsExactly("Nubank");
        }

        @Test
        void updatesACard() {
            CardResponse card = createCard("Nubank", 1_000_000L, 20, 5);

            CardResponse updated = session.patch(
                            path("/cards/" + card.id()),
                            new CardRequest("Nubank Ultravioleta", "MASTERCARD", 2_000_000L,
                                    (short) 10, (short) 20, account.id(), "#8A05BE"),
                            CardResponse.class)
                    .getBody();

            assertThat(updated.name()).isEqualTo("Nubank Ultravioleta");
            assertThat(updated.limitCents()).isEqualTo(2_000_000L);
            assertThat(updated.closingDay()).isEqualTo((short) 10);
        }

        @Test
        @DisplayName("a payment account from another workspace is refused")
        void rejectsForeignPaymentAccount() {
            ResponseEntity<ErrorResponse> response = session.post(
                    path("/cards"),
                    new CardRequest("Estranho", null, 1_000L, (short) 10, (short) 20, UUID.randomUUID(), null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
        }

        @Test
        @DisplayName("closing/due days outside 1–28 are rejected, so no month can lack the day")
        void rejectsDaysOutsideTheSupportedRange() {
            ResponseEntity<ErrorResponse> response = session.post(
                    path("/cards"),
                    new CardRequest("Inválido", null, 1_000L, (short) 31, (short) 20, null, null),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        }

        @Test
        @DisplayName("archiving hides the card from the default listing but keeps its history")
        void archivesACard() {
            CardResponse card = createCard("Nubank", 1_000_000L, 20, 5);

            ResponseEntity<CardResponse> archived =
                    session.post(path("/cards/" + card.id() + "/archive"), null, CardResponse.class);
            assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(archived.getBody().archived()).isTrue();

            // Idempotent, exactly like archiving an account.
            assertThat(session.post(path("/cards/" + card.id() + "/archive"), null, CardResponse.class)
                            .getBody()
                            .archived())
                    .isTrue();

            assertThat(session.get(path("/cards"), CardResponse[].class).getBody())
                    .as("gone from the default listing")
                    .isEmpty();
            assertThat(session.get(path("/cards?includeArchived=true"), CardResponse[].class)
                            .getBody())
                    .as("still there as history")
                    .extracting(CardResponse::id)
                    .containsExactly(card.id());
        }

        @Test
        @DisplayName("an archived card refuses new purchases but its statements stay readable")
        void archivedCardIsReadOnlyHistory() {
            CardResponse card = createCard("Nubank", 1_000_000L, 20, 5);
            createPurchase(card.id(), "Compra anterior", 30_000L, LocalDate.of(2026, 7, 10), 1);
            session.post(path("/cards/" + card.id() + "/archive"), null, CardResponse.class);

            ResponseEntity<ErrorResponse> refused = session.post(
                    path("/cards/" + card.id() + "/purchases"),
                    new CardPurchaseRequest(
                            someExpenseCategory().id(), "Nova compra", 1_000L, LocalDate.of(2026, 7, 15), (short) 1),
                    ErrorResponse.class);
            assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(refused.getBody().code()).isEqualTo(ErrorCode.CARD_ARCHIVED);

            assertThat(statementOfMonth(card.id(), "2026-07").totalCents())
                    .as("the bill it already ran up is still owed and still visible")
                    .isEqualTo(30_000L);
        }

        @Test
        void archivingACardOfAnotherWorkspaceIsNotFound() {
            CardResponse card = createCard("Nubank", 1_000_000L, 20, 5);
            register();

            ResponseEntity<ErrorResponse> response =
                    session.post(path("/cards/" + card.id() + "/archive"), null, ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.CARD_NOT_FOUND);
        }

        @Test
        void aCardOfAnotherWorkspaceIsNotReachable() {
            CardResponse card = createCard("Nubank", 1_000_000L, 20, 5);
            register();

            ResponseEntity<ErrorResponse> response = session.get(
                    path("/cards/" + card.id() + "/statements"), ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.CARD_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("available limit")
    class AvailableLimit {

        @Test
        @DisplayName("every installment consumes the limit up-front, future months included")
        void futureInstallmentsConsumeTheLimit() {
            CardResponse card = createCard("Nubank", 100_000L, 20, 5);
            // 600,00 in 3 — only the first third is billed this month, but all of it is committed.
            createPurchase(card.id(), "Notebook", 60_000L, LocalDate.of(2026, 7, 10), 3);

            assertThat(onlyCard().availableLimitCents()).isEqualTo(100_000L - 60_000L);
        }

        @Test
        @DisplayName("paying a statement gives its share of the limit back")
        void payingFreesTheLimit() {
            CardResponse card = createCard("Nubank", 100_000L, 20, 5);
            createPurchase(card.id(), "Notebook", 60_000L, LocalDate.of(2026, 7, 10), 3);
            StatementResponse july = statementOfMonth(card.id(), "2026-07");

            session.post(
                    path("/statements/" + july.id() + "/payments"),
                    new StatementPaymentRequest(account.id(), july.totalCents(), LocalDate.of(2026, 8, 5)),
                    StatementResponse.class);

            // The July third is settled; the other two thirds are still committed.
            assertThat(onlyCard().availableLimitCents()).isEqualTo(100_000L - 40_000L);
        }

        @Test
        @DisplayName("a partial payment frees exactly what it settled")
        void partialPaymentFreesItsShare() {
            CardResponse card = createCard("Nubank", 100_000L, 20, 5);
            createPurchase(card.id(), "Mercado", 30_000L, LocalDate.of(2026, 7, 10), 1);
            StatementResponse july = statementOfMonth(card.id(), "2026-07");

            session.post(
                    path("/statements/" + july.id() + "/payments"),
                    new StatementPaymentRequest(account.id(), 10_000L, LocalDate.of(2026, 8, 5)),
                    StatementResponse.class);

            assertThat(onlyCard().availableLimitCents()).isEqualTo(100_000L - 20_000L);
        }

        @Test
        @DisplayName("spending past the limit reports a negative figure rather than hiding it")
        void limitCanGoNegative() {
            CardResponse card = createCard("Nubank", 10_000L, 20, 5);
            createPurchase(card.id(), "Notebook", 25_000L, LocalDate.of(2026, 7, 10), 1);

            assertThat(onlyCard().availableLimitCents()).isEqualTo(-15_000L);
        }

        private CardResponse onlyCard() {
            CardResponse[] cards = session.get(path("/cards"), CardResponse[].class).getBody();
            assertThat(cards).hasSize(1);
            return cards[0];
        }
    }

    @Nested
    @DisplayName("statements are materialized on demand (R3)")
    class LazyStatements {

        @Test
        @DisplayName("asking for a window creates the months that have none yet")
        void windowMaterializesMissingMonths() {
            CardResponse card = createCard("Nubank", 1_000_000L, 20, 5);

            StatementResponse[] statements = listStatements(card.id(), "?from=2026-07&to=2026-10");

            assertThat(statements)
                    .extracting(StatementResponse::referenceMonth)
                    .containsExactly("2026-07", "2026-08", "2026-09", "2026-10");
            assertThat(statements).extracting(StatementResponse::totalCents).containsOnly(0L);
        }

        @Test
        @DisplayName("asking twice returns the same statements, not duplicates")
        void materializationIsIdempotent() {
            CardResponse card = createCard("Nubank", 1_000_000L, 20, 5);

            StatementResponse[] first = listStatements(card.id(), "?from=2026-07&to=2026-09");
            StatementResponse[] second = listStatements(card.id(), "?from=2026-07&to=2026-09");

            assertThat(second)
                    .extracting(StatementResponse::id)
                    .containsExactly(java.util.Arrays.stream(first).map(StatementResponse::id).toArray(UUID[]::new));
        }

        @Test
        @DisplayName("closing/due dates are frozen when the statement is created")
        void datesAreFrozenAtCreation() {
            CardResponse card = createCard("Nubank", 1_000_000L, 20, 5);
            StatementResponse before = statementOfMonth(card.id(), "2026-07");
            assertThat(before.closingDate()).isEqualTo(LocalDate.of(2026, 7, 20));

            // Moving the card's closing day must not rewrite a bill that already exists.
            session.patch(
                    path("/cards/" + card.id()),
                    new CardRequest("Nubank", null, 1_000_000L, (short) 10, (short) 20, null, null),
                    CardResponse.class);

            assertThat(statementOfMonth(card.id(), "2026-07").closingDate()).isEqualTo(LocalDate.of(2026, 7, 20));
            // A month materialized after the change follows the new schedule.
            assertThat(statementOfMonth(card.id(), "2027-01").closingDate()).isEqualTo(LocalDate.of(2027, 1, 10));
        }
    }
}
