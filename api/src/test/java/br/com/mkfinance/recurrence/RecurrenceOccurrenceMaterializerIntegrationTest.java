package br.com.mkfinance.recurrence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.application.recurrence.RecurrenceOccurrenceMaterializer;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.RecurrenceFrequency;
import br.com.mkfinance.domain.model.RecurrenceOccurrence;
import br.com.mkfinance.domain.model.RecurrenceOccurrenceStatus;
import br.com.mkfinance.domain.model.RecurrenceRule;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.infrastructure.persistence.RecurrenceOccurrenceRepository;
import br.com.mkfinance.infrastructure.persistence.RecurrenceRuleRepository;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * BE-7 aceite: reading the same competency twice never duplicates a transaction. Reuses the
 * lazy-per-competency pattern of {@code application/card/StatementProvider}.
 */
class RecurrenceOccurrenceMaterializerIntegrationTest extends AbstractRecurrenceIntegrationTest {

    @Autowired
    private RecurrenceOccurrenceMaterializer materializer;

    @Autowired
    private RecurrenceRuleRepository ruleRepository;

    @Autowired
    private RecurrenceOccurrenceRepository occurrenceRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private AccountResponse account;
    private CategoryResponse expenseCategory;

    @BeforeEach
    void setUp() {
        register();
        account = createAccount("Conta Corrente", 0L);
        expenseCategory = someCategory(CategoryType.EXPENSE);
    }

    private RecurrenceRule monthlyRule(int dayOfMonth, LocalDate startDate) {
        RecurrenceRule rule = RecurrenceRule.builder()
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
                .build();
        return ruleRepository.save(rule);
    }

    @Test
    void materializesAPendingTransactionForEachMissingOccurrence() {
        monthlyRule(5, LocalDate.of(2026, 1, 1));

        materializer.materializeMonth(workspaceId, YearMonth.of(2026, 8));

        List<Transaction> transactions =
                transactionRepository.findByWorkspaceIdAndStatusAndDateBetweenOrderByDateAsc(
                        workspaceId, TransactionStatus.PENDING, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getDate()).isEqualTo(LocalDate.of(2026, 8, 5));
        assertThat(transactions.get(0).getAmountCents()).isEqualTo(120_000L);
        assertThat(transactions.get(0).getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void materializingTheSameMonthTwiceDoesNotDuplicateTransactions() {
        RecurrenceRule rule = monthlyRule(5, LocalDate.of(2026, 1, 1));

        materializer.materializeMonth(workspaceId, YearMonth.of(2026, 8));
        materializer.materializeMonth(workspaceId, YearMonth.of(2026, 8));

        List<Transaction> transactions =
                transactionRepository.findByWorkspaceIdAndStatusAndDateBetweenOrderByDateAsc(
                        workspaceId, TransactionStatus.PENDING, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertThat(transactions).hasSize(1);

        List<RecurrenceOccurrence> occurrences =
                occurrenceRepository.findByRuleIdAndOccurrenceDateIn(rule.getId(), List.of(LocalDate.of(2026, 8, 5)));
        assertThat(occurrences).hasSize(1);
        assertThat(occurrences.get(0).getStatus()).isEqualTo(RecurrenceOccurrenceStatus.GENERATED);
        assertThat(occurrences.get(0).getTransactionId()).isEqualTo(transactions.get(0).getId());
    }

    @Test
    void materializedTransactionCarriesTheRecurrenceId() {
        RecurrenceRule rule = monthlyRule(5, LocalDate.of(2026, 1, 1));

        materializer.materializeMonth(workspaceId, YearMonth.of(2026, 8));

        Transaction transaction = transactionRepository
                .findByWorkspaceIdAndStatusAndDateBetweenOrderByDateAsc(
                        workspaceId, TransactionStatus.PENDING, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                .get(0);
        assertThat(transaction.getRecurrenceId()).isEqualTo(rule.getId());
    }

    @Test
    void aDeactivatedRuleProducesNothingButKeepsWhatWasAlreadyMaterialized() {
        RecurrenceRule rule = monthlyRule(5, LocalDate.of(2026, 1, 1));
        materializer.materializeMonth(workspaceId, YearMonth.of(2026, 8));

        rule.setActive(false);
        ruleRepository.save(rule);
        materializer.materializeMonth(workspaceId, YearMonth.of(2026, 9));

        assertThat(transactionRepository.findByWorkspaceIdAndStatusAndDateBetweenOrderByDateAsc(
                        workspaceId, TransactionStatus.PENDING, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)))
                .isEmpty();
        assertThat(transactionRepository.findByWorkspaceIdAndStatusAndDateBetweenOrderByDateAsc(
                        workspaceId, TransactionStatus.PENDING, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                .hasSize(1);
    }

    @Test
    void aMonthTheRuleDoesNotReachYieldsNoOccurrence() {
        monthlyRule(5, LocalDate.of(2026, 9, 1));

        materializer.materializeMonth(workspaceId, YearMonth.of(2026, 8));

        assertThat(transactionRepository.findByWorkspaceIdAndStatusAndDateBetweenOrderByDateAsc(
                        workspaceId, TransactionStatus.PENDING, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)))
                .isEmpty();
    }
}
