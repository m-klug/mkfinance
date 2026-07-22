package br.com.mkfinance.card;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.card.CardPurchaseRequest;
import br.com.mkfinance.api.card.CardPurchaseResponse;
import br.com.mkfinance.api.card.CardPurchaseResponse.InstallmentResponse;
import br.com.mkfinance.api.card.CardResponse;
import br.com.mkfinance.api.card.StatementPaymentRequest;
import br.com.mkfinance.api.card.StatementResponse;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.domain.exception.ErrorCode;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.StatementStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Allocating purchases to statements (R1, R3) and the limits on editing them (R6). */
class CardPurchaseIntegrationTest extends AbstractCardIntegrationTest {

    private static final int CLOSING_DAY = 20;

    private AccountResponse account;
    private CardResponse card;

    @BeforeEach
    void setUp() {
        register();
        account = createAccount("Conta corrente", 1_000_000L);
        card = createCard("Nubank", 1_000_000L, CLOSING_DAY, 5, account.id());
    }

    @Nested
    @DisplayName("allocation")
    class Allocation {

        @Test
        @DisplayName("bought after the closing day → the next statement, not the current one")
        void afterClosingGoesToTheNextStatement() {
            CardPurchaseResponse purchase =
                    createPurchase(card.id(), "Mercado", 5_000L, LocalDate.of(2026, 7, 21), 1);

            assertThat(purchase.installments())
                    .extracting(InstallmentResponse::statementMonth)
                    .containsExactly("2026-08");
            assertThat(statementOfMonth(card.id(), "2026-07").totalCents()).isZero();
            assertThat(statementOfMonth(card.id(), "2026-08").totalCents()).isEqualTo(5_000L);
        }

        @Test
        @DisplayName("12 installments walk consecutive statements across the turn of the year")
        void twelveInstallmentsCrossTheYear() {
            CardPurchaseResponse purchase =
                    createPurchase(card.id(), "Viagem", 120_007L, LocalDate.of(2026, 8, 25), 12);

            assertThat(purchase.installments()).hasSize(12);
            assertThat(purchase.installments().get(0).statementMonth()).isEqualTo("2026-09");
            assertThat(purchase.installments().get(4).statementMonth()).isEqualTo("2027-01");
            assertThat(purchase.installments().get(11).statementMonth()).isEqualTo("2027-08");

            // R1: the remainder rides on the last installment and nothing is lost.
            long sum = purchase.installments().stream()
                    .mapToLong(InstallmentResponse::amountCents)
                    .sum();
            assertThat(sum).isEqualTo(120_007L);
            assertThat(purchase.installments().get(11).amountCents())
                    .isGreaterThan(purchase.installments().get(0).amountCents());

            assertThat(statementOfMonth(card.id(), "2027-08").totalCents())
                    .isEqualTo(purchase.installments().get(11).amountCents());
        }

        @Test
        @DisplayName("several purchases in the same month add up on one statement")
        void purchasesAccumulateOnTheStatement() {
            createPurchase(card.id(), "Mercado", 5_000L, LocalDate.of(2026, 7, 3), 1);
            createPurchase(card.id(), "Farmácia", 2_500L, LocalDate.of(2026, 7, 11), 1);

            assertThat(statementOfMonth(card.id(), "2026-07").totalCents()).isEqualTo(7_500L);
        }

        @Test
        @DisplayName("an income category cannot classify a purchase")
        void rejectsIncomeCategory() {
            ResponseEntity<ErrorResponse> response = session.post(
                    path("/cards/" + card.id() + "/purchases"),
                    new CardPurchaseRequest(
                            someCategory(CategoryType.INCOME).id(),
                            "Errado",
                            5_000L,
                            LocalDate.of(2026, 7, 10),
                            (short) 1),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.CATEGORY_TYPE_MISMATCH);
        }

        @Test
        void rejectsPurchaseOnACardOfAnotherWorkspace() {
            UUID foreignCardId = card.id();
            register();

            ResponseEntity<CardPurchaseResponse> response =
                    purchaseResponse(foreignCardId, "Mercado", 5_000L, LocalDate.of(2026, 7, 10), 1);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("editing (R6)")
    class Editing {

        @Test
        @DisplayName("re-allocates from scratch when the amount and installment count change")
        void reallocatesOnEdit() {
            CardPurchaseResponse purchase =
                    createPurchase(card.id(), "Notebook", 10_000L, LocalDate.of(2026, 7, 10), 3);
            assertThat(statementOfMonth(card.id(), "2026-09").totalCents()).isEqualTo(3_334L);

            CardPurchaseResponse updated = update(purchase.id(), "Notebook Pro", 9_000L, LocalDate.of(2026, 7, 10), 2);

            assertThat(updated.installments())
                    .extracting(InstallmentResponse::amountCents)
                    .containsExactly(4_500L, 4_500L);
            assertThat(statementOfMonth(card.id(), "2026-07").totalCents()).isEqualTo(4_500L);
            assertThat(statementOfMonth(card.id(), "2026-08").totalCents()).isEqualTo(4_500L);
            // The third installment is gone, so September is empty again.
            assertThat(statementOfMonth(card.id(), "2026-09").totalCents()).isZero();
        }

        @Test
        @DisplayName("moving the purchase date moves it to another statement")
        void movingTheDateMovesTheStatement() {
            CardPurchaseResponse purchase =
                    createPurchase(card.id(), "Mercado", 5_000L, LocalDate.of(2026, 7, 10), 1);

            update(purchase.id(), "Mercado", 5_000L, LocalDate.of(2026, 7, 25), 1);

            assertThat(statementOfMonth(card.id(), "2026-07").totalCents()).isZero();
            assertThat(statementOfMonth(card.id(), "2026-08").totalCents()).isEqualTo(5_000L);
        }

        @Test
        void deletingRemovesTheInstallmentsFromTheirStatements() {
            CardPurchaseResponse purchase =
                    createPurchase(card.id(), "Notebook", 10_000L, LocalDate.of(2026, 7, 10), 3);

            ResponseEntity<Void> response = session.delete(path("/purchases/" + purchase.id()), Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(statementOfMonth(card.id(), "2026-07").totalCents()).isZero();
            assertThat(statementOfMonth(card.id(), "2026-09").totalCents()).isZero();
        }

        @Test
        @DisplayName("a purchase sitting on a PAID statement cannot be edited")
        void cannotEditAPurchaseOnAPaidStatement() {
            CardPurchaseResponse purchase =
                    createPurchase(card.id(), "Notebook", 10_000L, LocalDate.of(2026, 7, 10), 3);
            payInFull(statementOfMonth(card.id(), "2026-07"));

            ResponseEntity<ErrorResponse> response = session.patch(
                    path("/purchases/" + purchase.id()),
                    new CardPurchaseRequest(
                            someExpenseCategory().id(), "Notebook Pro", 9_000L, LocalDate.of(2026, 7, 10), (short) 2),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.STATEMENT_IMMUTABLE);
            // The offending month travels with the error so the client can say which bill blocks it.
            assertThat(response.getBody().params()).containsEntry("statementMonth", "2026-07");
            // Nothing changed.
            assertThat(statementOfMonth(card.id(), "2026-09").totalCents()).isEqualTo(3_334L);
        }

        @Test
        @DisplayName("a purchase sitting on a PAID statement cannot be deleted either")
        void cannotDeleteAPurchaseOnAPaidStatement() {
            CardPurchaseResponse purchase =
                    createPurchase(card.id(), "Notebook", 10_000L, LocalDate.of(2026, 7, 10), 3);
            payInFull(statementOfMonth(card.id(), "2026-07"));

            ResponseEntity<ErrorResponse> response =
                    session.delete(path("/purchases/" + purchase.id()), ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.STATEMENT_IMMUTABLE);
            assertThat(statementOfMonth(card.id(), "2026-07").totalCents()).isEqualTo(3_333L);
        }

        @Test
        @DisplayName("an edit that would move installments ONTO a paid statement is refused")
        void cannotMoveInstallmentsOntoAPaidStatement() {
            // July is paid and closed for good.
            createPurchase(card.id(), "Mercado", 5_000L, LocalDate.of(2026, 7, 10), 1);
            payInFull(statementOfMonth(card.id(), "2026-07"));

            // A separate, still-editable purchase on August.
            CardPurchaseResponse august =
                    createPurchase(card.id(), "Livros", 4_000L, LocalDate.of(2026, 7, 25), 1);

            // Backdating it would land it on the paid July statement.
            ResponseEntity<ErrorResponse> response = session.patch(
                    path("/purchases/" + august.id()),
                    new CardPurchaseRequest(
                            someExpenseCategory().id(), "Livros", 4_000L, LocalDate.of(2026, 7, 5), (short) 1),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.STATEMENT_IMMUTABLE);
            assertThat(statementOfMonth(card.id(), "2026-07").totalCents()).isEqualTo(5_000L);
            assertThat(statementOfMonth(card.id(), "2026-08").totalCents()).isEqualTo(4_000L);
        }

        @Test
        @DisplayName("installments on statements that are merely PARTIAL stay editable")
        void partiallyPaidStatementsRemainEditable() {
            CardPurchaseResponse purchase =
                    createPurchase(card.id(), "Notebook", 10_000L, LocalDate.of(2026, 7, 10), 3);
            StatementResponse july = statementOfMonth(card.id(), "2026-07");
            session.post(
                    path("/statements/" + july.id() + "/payments"),
                    new StatementPaymentRequest(account.id(), 1_000L, LocalDate.of(2026, 8, 5)),
                    StatementResponse.class);
            assertThat(statementOfMonth(card.id(), "2026-07").status()).isEqualTo(StatementStatus.PARTIAL);

            CardPurchaseResponse updated = update(purchase.id(), "Notebook", 6_000L, LocalDate.of(2026, 7, 10), 3);

            assertThat(updated.installments()).extracting(InstallmentResponse::amountCents).containsExactly(2_000L, 2_000L, 2_000L);
            StatementResponse afterEdit = statementOfMonth(card.id(), "2026-07");
            assertThat(afterEdit.totalCents()).isEqualTo(2_000L);
            assertThat(afterEdit.paidCents()).isEqualTo(1_000L);
            assertThat(afterEdit.status()).isEqualTo(StatementStatus.PARTIAL);
        }

        @Test
        @DisplayName("shrinking a purchase until the payment covers it completes the statement")
        void statusFollowsTheNewTotalAfterAnEdit() {
            CardPurchaseResponse purchase =
                    createPurchase(card.id(), "Notebook", 10_000L, LocalDate.of(2026, 7, 10), 1);
            StatementResponse july = statementOfMonth(card.id(), "2026-07");
            session.post(
                    path("/statements/" + july.id() + "/payments"),
                    new StatementPaymentRequest(account.id(), 4_000L, LocalDate.of(2026, 8, 5)),
                    StatementResponse.class);

            update(purchase.id(), "Notebook", 4_000L, LocalDate.of(2026, 7, 10), 1);

            assertThat(statementOfMonth(card.id(), "2026-07").status()).isEqualTo(StatementStatus.PAID);
        }
    }

    private CardPurchaseResponse update(
            UUID purchaseId, String description, long totalAmountCents, LocalDate purchaseDate, int installments) {
        ResponseEntity<CardPurchaseResponse> response = session.patch(
                path("/purchases/" + purchaseId),
                new CardPurchaseRequest(
                        someExpenseCategory().id(),
                        description,
                        totalAmountCents,
                        purchaseDate,
                        (short) installments),
                CardPurchaseResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void payInFull(StatementResponse statement) {
        ResponseEntity<StatementResponse> response = session.post(
                path("/statements/" + statement.id() + "/payments"),
                new StatementPaymentRequest(account.id(), statement.totalCents(), LocalDate.of(2026, 8, 5)),
                StatementResponse.class);
        assertThat(response.getBody().status()).isEqualTo(StatementStatus.PAID);
    }
}
