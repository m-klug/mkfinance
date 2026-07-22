package br.com.mkfinance.card;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.card.CardPurchaseResponse;
import br.com.mkfinance.api.card.CardResponse;
import br.com.mkfinance.api.card.StatementDetailResponse;
import br.com.mkfinance.api.card.StatementPaymentRequest;
import br.com.mkfinance.api.card.StatementResponse;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.api.transaction.TransactionPageResponse;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.domain.exception.ErrorCode;
import br.com.mkfinance.domain.model.StatementStatus;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Purchase → statement → payment, end to end against a real Postgres.
 *
 * <p>The first nested class is the E2 gate scenario: a purchase made <em>on</em> the closing day,
 * split in 3, and the statement payment debiting the account.
 */
class StatementPaymentIntegrationTest extends AbstractCardIntegrationTest {

    private static final int CLOSING_DAY = 20;
    private static final int DUE_DAY = 5;

    private AccountResponse account;
    private CardResponse card;

    @BeforeEach
    void setUp() {
        register();
        account = createAccount("Conta corrente", 500_000L);
        card = createCard("Nubank", 1_000_000L, CLOSING_DAY, DUE_DAY, account.id());
    }

    @Nested
    @DisplayName("E2 gate: purchase on the closing day, in 3, then paying the bill")
    class CanonicalScenario {

        @Test
        void purchaseOnClosingDaySplitsInThreeAndThePaymentDebitsTheAccount() {
            // Bought exactly on the closing day: R3 keeps it on the statement still open (July),
            // not the next one — the boundary case users notice when an app gets it wrong.
            LocalDate purchaseDate = LocalDate.of(2026, 7, CLOSING_DAY);

            CardPurchaseResponse purchase = createPurchase(card.id(), "Notebook", 10_000L, purchaseDate, 3);

            // R1: 100,00 in 3 → 33,33 + 33,33 + 33,34, the remainder on the last installment.
            assertThat(purchase.installments())
                    .extracting(CardPurchaseResponse.InstallmentResponse::amountCents)
                    .containsExactly(3_333L, 3_333L, 3_334L);
            // R3: consecutive statements starting with the current one.
            assertThat(purchase.installments())
                    .extracting(CardPurchaseResponse.InstallmentResponse::statementMonth)
                    .containsExactly("2026-07", "2026-08", "2026-09");

            StatementResponse july = statementOfMonth(card.id(), "2026-07");
            assertThat(july.totalCents()).isEqualTo(3_333L);
            assertThat(july.paidCents()).isZero();
            assertThat(july.closingDate()).isEqualTo(LocalDate.of(2026, 7, 20));
            assertThat(july.dueDate()).isEqualTo(LocalDate.of(2026, 8, 5));

            // Paying the July bill in full.
            StatementResponse paid = pay(july.id(), account.id(), 3_333L, LocalDate.of(2026, 8, 5));

            assertThat(paid.status()).isEqualTo(StatementStatus.PAID);
            assertThat(paid.paidCents()).isEqualTo(3_333L);

            // R2: the debit exists on the account as a settled EXPENSE — the money really left.
            TransactionResponse debit = onlyTransactionOfAccount(account.id());
            assertThat(debit.type()).isEqualTo(TransactionType.EXPENSE);
            assertThat(debit.status()).isEqualTo(TransactionStatus.SETTLED);
            assertThat(debit.amountCents()).isEqualTo(3_333L);
            assertThat(debit.date()).isEqualTo(LocalDate.of(2026, 8, 5));
            assertThat(debit.accountId()).isEqualTo(account.id());

            // R2: booked under the system category, which reports by category exclude — otherwise the
            // spending would be counted twice, once on the purchase and once on the payment.
            assertThat(categoryById(debit.categoryId()).system()).isTrue();

            // The two remaining installments are untouched and still owed.
            assertThat(statementOfMonth(card.id(), "2026-08").totalCents()).isEqualTo(3_333L);
            assertThat(statementOfMonth(card.id(), "2026-09").totalCents()).isEqualTo(3_334L);
            assertThat(statementOfMonth(card.id(), "2026-08").status()).isNotEqualTo(StatementStatus.PAID);
        }
    }

    @Nested
    @DisplayName("partial payment")
    class PartialPayment {

        @Test
        @DisplayName("paying less than the total leaves the statement PARTIAL, with no interest (§9.5)")
        void partialPaymentLeavesTheRemainderOutstanding() {
            createPurchase(card.id(), "Mercado", 20_000L, LocalDate.of(2026, 7, 10), 1);
            StatementResponse july = statementOfMonth(card.id(), "2026-07");
            assertThat(july.totalCents()).isEqualTo(20_000L);

            StatementResponse afterPartial = pay(july.id(), account.id(), 8_000L, LocalDate.of(2026, 8, 5));

            assertThat(afterPartial.status()).isEqualTo(StatementStatus.PARTIAL);
            assertThat(afterPartial.paidCents()).isEqualTo(8_000L);
            // No interest is added: the statement total is exactly what it was.
            assertThat(afterPartial.totalCents()).isEqualTo(20_000L);
        }

        @Test
        @DisplayName("a second payment settling the rest completes the statement")
        void secondPaymentCompletesTheStatement() {
            createPurchase(card.id(), "Mercado", 20_000L, LocalDate.of(2026, 7, 10), 1);
            UUID statementId = statementOfMonth(card.id(), "2026-07").id();

            pay(statementId, account.id(), 8_000L, LocalDate.of(2026, 8, 5));
            StatementResponse settled = pay(statementId, account.id(), 12_000L, LocalDate.of(2026, 8, 20));

            assertThat(settled.status()).isEqualTo(StatementStatus.PAID);
            assertThat(settled.paidCents()).isEqualTo(20_000L);
            // Two separate debits on the account, not one merged row.
            assertThat(transactionsOfAccount(account.id()).content()).hasSize(2);
        }

        @Test
        @DisplayName("paying more than what is owed is refused — the MVP has no credit balance")
        void overpaymentIsRefused() {
            createPurchase(card.id(), "Mercado", 20_000L, LocalDate.of(2026, 7, 10), 1);
            UUID statementId = statementOfMonth(card.id(), "2026-07").id();
            pay(statementId, account.id(), 15_000L, LocalDate.of(2026, 8, 5));

            ResponseEntity<ErrorResponse> response = payRaw(statementId, account.id(), 6_000L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.PAYMENT_EXCEEDS_REMAINING);
            // Nothing was debited for the refused attempt.
            assertThat(transactionsOfAccount(account.id()).content()).hasSize(1);
        }

        @Test
        void payingAnAlreadyPaidStatementIsRefused() {
            createPurchase(card.id(), "Mercado", 20_000L, LocalDate.of(2026, 7, 10), 1);
            UUID statementId = statementOfMonth(card.id(), "2026-07").id();
            pay(statementId, account.id(), 20_000L, LocalDate.of(2026, 8, 5));

            ResponseEntity<ErrorResponse> response = payRaw(statementId, account.id(), 100L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.STATEMENT_ALREADY_PAID);
        }

        @Test
        @DisplayName("a statement with nothing on it cannot be paid")
        void emptyStatementCannotBePaid() {
            UUID statementId = statementOfMonth(card.id(), "2026-07").id();

            ResponseEntity<ErrorResponse> response = payRaw(statementId, account.id(), 100L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.STATEMENT_EMPTY);
        }
    }

    @Nested
    @DisplayName("statement detail")
    class Detail {

        @Test
        @DisplayName("line items carry the purchase context needed to recognize each charge")
        void itemsCarryPurchaseContext() {
            createPurchase(card.id(), "Notebook", 10_000L, LocalDate.of(2026, 7, 10), 3);
            createPurchase(card.id(), "Mercado", 5_000L, LocalDate.of(2026, 7, 12), 1);
            UUID statementId = statementOfMonth(card.id(), "2026-07").id();

            StatementDetailResponse detail = session.get(
                            path("/statements/" + statementId), StatementDetailResponse.class)
                    .getBody();

            assertThat(detail.totalCents()).isEqualTo(3_333L + 5_000L);
            assertThat(detail.items())
                    .extracting(
                            StatementDetailResponse.StatementItemResponse::description,
                            StatementDetailResponse.StatementItemResponse::installmentNumber,
                            StatementDetailResponse.StatementItemResponse::installmentCount,
                            StatementDetailResponse.StatementItemResponse::amountCents)
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple("Notebook", (short) 1, (short) 3, 3_333L),
                            org.assertj.core.groups.Tuple.tuple("Mercado", (short) 1, (short) 1, 5_000L));
            // The purchase date travels with the line, so reports can count by competency (R2).
            assertThat(detail.items())
                    .extracting(StatementDetailResponse.StatementItemResponse::purchaseDate)
                    .containsOnly(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 12));
        }
    }

    @Nested
    @DisplayName("workspace isolation (R8)")
    class Isolation {

        @Test
        void aStatementOfAnotherWorkspaceIsNotReachable() {
            createPurchase(card.id(), "Mercado", 5_000L, LocalDate.of(2026, 7, 10), 1);
            UUID otherWorkspaceStatementId = statementOfMonth(card.id(), "2026-07").id();

            // A second, unrelated user asking for the first one's statement through their own workspace.
            register();

            ResponseEntity<ErrorResponse> response = session.get(
                    path("/statements/" + otherWorkspaceStatementId), ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo(ErrorCode.STATEMENT_NOT_FOUND);
        }
    }

    private StatementResponse pay(UUID statementId, UUID accountId, long amountCents, LocalDate date) {
        ResponseEntity<StatementResponse> response = session.post(
                path("/statements/" + statementId + "/payments"),
                new StatementPaymentRequest(accountId, amountCents, date),
                StatementResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ResponseEntity<ErrorResponse> payRaw(UUID statementId, UUID accountId, long amountCents) {
        return session.post(
                path("/statements/" + statementId + "/payments"),
                new StatementPaymentRequest(accountId, amountCents, LocalDate.of(2026, 8, 10)),
                ErrorResponse.class);
    }

    private TransactionPageResponse transactionsOfAccount(UUID accountId) {
        return session.get(path("/transactions?accountId=" + accountId), TransactionPageResponse.class)
                .getBody();
    }

    private TransactionResponse onlyTransactionOfAccount(UUID accountId) {
        TransactionPageResponse page = transactionsOfAccount(accountId);
        assertThat(page.content()).hasSize(1);
        return page.content().get(0);
    }

    private CategoryResponse categoryById(UUID categoryId) {
        CategoryResponse[] categories =
                session.get(path("/categories"), CategoryResponse[].class).getBody();
        return Arrays.stream(categories)
                .filter(category -> category.id().equals(categoryId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Category " + categoryId + " not in the workspace"));
    }
}
