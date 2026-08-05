package br.com.mkfinance.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.calendar.CalendarMonthResponse;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.api.transaction.TransactionPageResponse;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.domain.model.AccountType;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.RecurrenceFrequency;
import br.com.mkfinance.domain.model.RecurrenceRule;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.infrastructure.persistence.RecurrenceRuleRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * BE-8 aceite B — the extrato materializes recurrence occurrences too, not only the calendar
 * (decision 30/07/2026, DESENVOLVIMENTO-FASE2 §4 E6/BE-8). {@code RecurrenceOccurrenceMaterializer}
 * itself is BE-7's and already covered in {@code recurrence/RecurrenceOccurrenceMaterializerIntegrationTest}
 * — this class only proves {@code GET /transactions} calls it, respects the bounded-window rule, and
 * never duplicates.
 */
class TransactionExtratoMaterializationIntegrationTest extends AbstractTransactionIntegrationTest {

    @Autowired
    private RecurrenceRuleRepository ruleRepository;

    private AccountResponse account;
    private CategoryResponse expenseCategory;

    @BeforeEach
    void setUp() {
        register();
        account = createAccount("Conta Corrente", AccountType.CHECKING, 0L);
        expenseCategory = someCategory(CategoryType.EXPENSE);
    }

    private void monthlySalaryRule(int dayOfMonth, LocalDate startDate) {
        ruleRepository.save(RecurrenceRule.builder()
                .workspaceId(workspaceId)
                .type(TransactionType.EXPENSE)
                .accountId(account.id())
                .categoryId(expenseCategory.id())
                .amountCents(120_000L)
                .description("Aluguel")
                .frequency(RecurrenceFrequency.MONTHLY)
                .dayOfMonth(dayOfMonth)
                .startDate(startDate)
                .active(true)
                .build());
    }

    private TransactionPageResponse listWith(String query) {
        return session.get(path("/transactions" + query), TransactionPageResponse.class).getBody();
    }

    @Test
    @DisplayName("a bounded window materializes the pending occurrences it touches")
    void extratoWindowMaterializesPendingOccurrences() {
        monthlySalaryRule(5, LocalDate.of(2026, 1, 1));

        TransactionPageResponse page = listWith("?from=2026-08-01&to=2026-08-31");

        assertThat(page.totalElements()).isEqualTo(1L);
        TransactionResponse occurrence = page.content().get(0);
        assertThat(occurrence.status()).isEqualTo(TransactionStatus.PENDING);
        assertThat(occurrence.recurrenceId()).isNotNull();
        assertThat(occurrence.date()).isEqualTo(LocalDate.of(2026, 8, 5));
    }

    @Test
    @DisplayName("reading the same competency twice through the extrato never duplicates")
    void readingTheSameWindowTwiceDoesNotDuplicate() {
        monthlySalaryRule(5, LocalDate.of(2026, 1, 1));

        listWith("?from=2026-08-01&to=2026-08-31");
        TransactionPageResponse second = listWith("?from=2026-08-01&to=2026-08-31");

        assertThat(second.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("reading through the extrato first, then the calendar, never duplicates either")
    void readingExtratoThenCalendarDoesNotDuplicate() {
        monthlySalaryRule(5, LocalDate.of(2026, 1, 1));

        listWith("?from=2026-08-01&to=2026-08-31");
        CalendarMonthResponse calendar =
                session.get(path("/calendar?month=2026-08"), CalendarMonthResponse.class).getBody();

        assertThat(calendar.entries()).hasSize(1);
        assertThat(listWith("?from=2026-08-01&to=2026-08-31").totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("a query with no window materializes nothing")
    void aQueryWithNoWindowMaterializesNothing() {
        monthlySalaryRule(5, LocalDate.of(2026, 1, 1));

        TransactionPageResponse unbounded = listWith("");

        assertThat(unbounded.totalElements()).isZero();
    }

    @Test
    @DisplayName("a window only bounded on one side materializes nothing")
    void aHalfBoundedWindowMaterializesNothing() {
        monthlySalaryRule(5, LocalDate.of(2026, 1, 1));

        assertThat(listWith("?from=2026-08-01").totalElements()).isZero();
        assertThat(listWith("?to=2026-08-31").totalElements()).isZero();
    }

    @Test
    @DisplayName("a window wider than the materialization ceiling materializes nothing")
    void aWindowWiderThanTheCeilingMaterializesNothing() {
        monthlySalaryRule(5, LocalDate.of(2020, 1, 1));

        // 13 competencies (Jan 2026 through Jan 2027) — one past the 12-month cap.
        TransactionPageResponse tooWide = listWith("?from=2026-01-01&to=2027-01-31");

        assertThat(tooWide.totalElements()).isZero();
    }

    @Test
    @DisplayName("a window exactly at the materialization ceiling still materializes")
    void aWindowAtTheCeilingMaterializes() {
        monthlySalaryRule(5, LocalDate.of(2020, 1, 1));

        // Exactly 12 competencies (Jan 2026 through Dec 2026).
        TransactionPageResponse atCeiling = listWith("?from=2026-01-01&to=2026-12-31");

        assertThat(atCeiling.totalElements()).isEqualTo(12L);
    }
}
