package br.com.mkfinance.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.calendar.CalendarEntryResponse;
import br.com.mkfinance.api.calendar.CalendarMonthResponse;
import br.com.mkfinance.api.card.CardPurchaseResponse;
import br.com.mkfinance.api.card.CardResponse;
import br.com.mkfinance.api.card.StatementPaymentRequest;
import br.com.mkfinance.api.card.StatementResponse;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.api.recurrence.RecurrenceRequest;
import br.com.mkfinance.api.recurrence.RecurrenceResponse;
import br.com.mkfinance.api.transaction.TransactionRequest;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.application.calendar.CalendarEntryKind;
import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.RecurrenceFrequency;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.model.TransferDirection;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * BE-7 — the read-only month agenda: statement due dates and still-pending transactions, including
 * the ones a recurrence materializes on read.
 */
class CalendarIntegrationTest extends AbstractCalendarIntegrationTest {

    private AccountResponse account;
    private CategoryResponse expenseCategory;

    @BeforeEach
    void setUp() {
        register();
        account = createAccount("Conta Corrente", 500_000L);
        expenseCategory = someCategory(CategoryType.EXPENSE);
    }

    private CalendarMonthResponse calendar(String month) {
        return session.get(path("/calendar?month=" + month), CalendarMonthResponse.class).getBody();
    }

    @Test
    void includesPendingTransactionsOfTheMonth() {
        session.post(
                path("/transactions"),
                new TransactionRequest(
                        account.id(),
                        expenseCategory.id(),
                        TransactionType.EXPENSE,
                        20_000L,
                        LocalDate.of(2026, 8, 12),
                        "Conta de luz",
                        null,
                        TransactionStatus.PENDING, null),
                TransactionResponse.class);

        CalendarMonthResponse response = calendar("2026-08");

        assertThat(response.month()).isEqualTo("2026-08");
        assertThat(response.entries()).hasSize(1);
        CalendarEntryResponse entry = response.entries().get(0);
        assertThat(entry.kind()).isEqualTo(CalendarEntryKind.PENDING_TRANSACTION);
        assertThat(entry.description()).isEqualTo("Conta de luz");
        assertThat(entry.amountCents()).isEqualTo(20_000L);
        assertThat(entry.direction()).isEqualTo(TransferDirection.OUT);
        assertThat(entry.date()).isEqualTo(LocalDate.of(2026, 8, 12));
        assertThat(entry.recurrenceId()).isNull();
    }

    @Test
    void settledTransactionsDoNotAppearOnTheCalendar() {
        session.post(
                path("/transactions"),
                new TransactionRequest(
                        account.id(),
                        expenseCategory.id(),
                        TransactionType.EXPENSE,
                        20_000L,
                        LocalDate.of(2026, 8, 12),
                        "Mercado",
                        null,
                        null, null),
                TransactionResponse.class);

        assertThat(calendar("2026-08").entries()).isEmpty();
    }

    @Test
    void materializesRecurrenceOccurrencesAndBadgesThemWithRecurrenceId() {
        RecurrenceResponse rule = session.post(
                        path("/recurrences"),
                        new RecurrenceRequest(
                                TransactionType.EXPENSE,
                                account.id(),
                                expenseCategory.id(),
                                120_000L,
                                "Aluguel",
                                null,
                                RecurrenceFrequency.MONTHLY,
                                5,
                                null,
                                null,
                                LocalDate.of(2026, 1, 1),
                                null,
                                null),
                        RecurrenceResponse.class)
                .getBody();

        CalendarMonthResponse first = calendar("2026-08");
        assertThat(first.entries()).hasSize(1);
        CalendarEntryResponse entry = first.entries().get(0);
        assertThat(entry.kind()).isEqualTo(CalendarEntryKind.PENDING_TRANSACTION);
        assertThat(entry.recurrenceId()).isEqualTo(rule.id());
        assertThat(entry.date()).isEqualTo(LocalDate.of(2026, 8, 5));

        // Reading the same month twice does not double it.
        CalendarMonthResponse second = calendar("2026-08");
        assertThat(second.entries()).hasSize(1);
        assertThat(second.entries().get(0).transactionId()).isEqualTo(entry.transactionId());
    }

    @Test
    void includesStatementDueEntriesWithTheRemainingBalance() {
        CardResponse card = createCard("Cartão", 500_000L, 20, 10);
        CardPurchaseResponse purchase =
                createPurchase(card.id(), "Notebook", 300_000L, LocalDate.of(2026, 7, 15), 1);

        CalendarMonthResponse response = calendar("2026-08");

        assertThat(response.entries()).hasSize(1);
        CalendarEntryResponse entry = response.entries().get(0);
        assertThat(entry.kind()).isEqualTo(CalendarEntryKind.STATEMENT_DUE);
        assertThat(entry.description()).isEqualTo("Cartão");
        assertThat(entry.amountCents()).isEqualTo(300_000L);
        assertThat(entry.cardId()).isEqualTo(card.id());
        assertThat(entry.date()).isEqualTo(LocalDate.of(2026, 8, 10));
        assertThat(purchase.description()).isEqualTo("Notebook");
    }

    @Test
    void aFullyPaidStatementDoesNotAppearOnTheCalendar() {
        CardResponse card = createCard("Cartão", 500_000L, 20, 10);
        createPurchase(card.id(), "Notebook", 300_000L, LocalDate.of(2026, 7, 15), 1);
        List<StatementResponse> statements = List.of(session.get(
                        path("/cards/" + card.id() + "/statements?from=2026-07&to=2026-07"),
                        StatementResponse[].class)
                .getBody());
        StatementResponse statement = statements.get(0);

        session.post(
                path("/statements/" + statement.id() + "/payments"),
                new StatementPaymentRequest(account.id(), 300_000L, LocalDate.of(2026, 8, 1)),
                StatementResponse.class);

        assertThat(calendar("2026-08").entries()).isEmpty();
    }

    @Test
    void defaultsToTheCurrentMonthWhenOmitted() {
        CalendarMonthResponse response = session.get(path("/calendar"), CalendarMonthResponse.class).getBody();

        assertThat(response.month()).isEqualTo(YearMonth.now(BusinessClock.ZONE).toString());
    }
}
