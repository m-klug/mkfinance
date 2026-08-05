package br.com.mkfinance.recurrence;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.api.account.AccountResponse;
import br.com.mkfinance.api.category.CategoryResponse;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.api.error.FieldErrorDto;
import br.com.mkfinance.api.recurrence.RecurrenceRequest;
import br.com.mkfinance.api.recurrence.RecurrenceResponse;
import br.com.mkfinance.api.transaction.TransactionPageResponse;
import br.com.mkfinance.api.transaction.TransactionResponse;
import br.com.mkfinance.application.recurrence.RecurrenceOccurrenceMaterializer;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.RecurrenceFrequency;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** BE-7 — recurrence rule CRUD and the CT-1 scoped edit/delete (THIS_ONE / THIS_AND_FUTURE). */
class RecurrenceRuleIntegrationTest extends AbstractRecurrenceIntegrationTest {

    @Autowired
    private RecurrenceOccurrenceMaterializer materializer;

    private AccountResponse account;
    private CategoryResponse expenseCategory;
    private CategoryResponse incomeCategory;

    @BeforeEach
    void setUp() {
        register();
        account = createAccount("Conta Corrente", 0L);
        expenseCategory = someCategory(CategoryType.EXPENSE);
        incomeCategory = someCategory(CategoryType.INCOME);
    }

    private RecurrenceRequest monthlyRequest(int dayOfMonth, LocalDate startDate) {
        return new RecurrenceRequest(
                TransactionType.EXPENSE,
                account.id(),
                expenseCategory.id(),
                120_000L,
                "Aluguel",
                null,
                RecurrenceFrequency.MONTHLY,
                dayOfMonth,
                null,
                null,
                startDate,
                null,
                null);
    }

    private RecurrenceResponse createMonthly(int dayOfMonth, LocalDate startDate) {
        return session.post(path("/recurrences"), monthlyRequest(dayOfMonth, startDate), RecurrenceResponse.class)
                .getBody();
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void createsAMonthlyRule() {
            ResponseEntity<RecurrenceResponse> response = session.post(
                    path("/recurrences"), monthlyRequest(5, LocalDate.of(2026, 1, 1)), RecurrenceResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            RecurrenceResponse body = response.getBody();
            assertThat(body.id()).isNotNull();
            assertThat(body.frequency()).isEqualTo(RecurrenceFrequency.MONTHLY);
            assertThat(body.dayOfMonth()).isEqualTo(5);
            assertThat(body.active()).isTrue();
            assertThat(body.endDate()).isNull();
        }

        @Test
        @DisplayName("MONTHLY without dayOfMonth is a VALIDATION_ERROR pointing at the field")
        void monthlyWithoutDayOfMonthIsValidationError() {
            RecurrenceRequest request = new RecurrenceRequest(
                    TransactionType.EXPENSE,
                    account.id(),
                    expenseCategory.id(),
                    1_000L,
                    "Assinatura",
                    null,
                    RecurrenceFrequency.MONTHLY,
                    null,
                    null,
                    null,
                    LocalDate.of(2026, 1, 1),
                    null,
                    null);

            ResponseEntity<ErrorResponse> response = session.post(path("/recurrences"), request, ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getBody().fieldErrors()).extracting(FieldErrorDto::field).containsExactly("dayOfMonth");
        }

        @Test
        @DisplayName("WEEKLY without dayOfWeek is a VALIDATION_ERROR pointing at the field")
        void weeklyWithoutDayOfWeekIsValidationError() {
            RecurrenceRequest request = new RecurrenceRequest(
                    TransactionType.EXPENSE,
                    account.id(),
                    expenseCategory.id(),
                    1_000L,
                    "Feira",
                    null,
                    RecurrenceFrequency.WEEKLY,
                    null,
                    null,
                    null,
                    LocalDate.of(2026, 1, 1),
                    null,
                    null);

            ResponseEntity<ErrorResponse> response = session.post(path("/recurrences"), request, ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().fieldErrors()).extracting(FieldErrorDto::field).containsExactly("dayOfWeek");
        }

        @Test
        @DisplayName("YEARLY without dayOfMonth or monthOfYear reports both fields")
        void yearlyWithoutDayOfMonthOrMonthOfYearIsValidationError() {
            RecurrenceRequest request = new RecurrenceRequest(
                    TransactionType.INCOME,
                    account.id(),
                    incomeCategory.id(),
                    500_00L,
                    "Décimo terceiro",
                    null,
                    RecurrenceFrequency.YEARLY,
                    null,
                    null,
                    null,
                    LocalDate.of(2020, 1, 1),
                    null,
                    null);

            ResponseEntity<ErrorResponse> response = session.post(path("/recurrences"), request, ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().fieldErrors())
                    .extracting(FieldErrorDto::field)
                    .containsExactlyInAnyOrder("dayOfMonth", "monthOfYear");
        }
    }

    @Nested
    @DisplayName("list")
    class List_ {

        @Test
        void omittingActiveListsEveryRuleIncludingClosedOnes() {
            RecurrenceResponse rule = createMonthly(5, LocalDate.of(2026, 1, 1));
            session.patch(
                    path("/recurrences/" + rule.id() + "?scope=THIS_AND_FUTURE&occurrenceDate=2026-06-05"),
                    monthlyRequest(10, LocalDate.of(2026, 1, 1)),
                    RecurrenceResponse.class);

            RecurrenceResponse[] all = session.get(path("/recurrences"), RecurrenceResponse[].class).getBody();
            RecurrenceResponse[] activeOnly =
                    session.get(path("/recurrences?active=true"), RecurrenceResponse[].class).getBody();

            assertThat(all).hasSize(2);
            assertThat(activeOnly).hasSize(1);
        }
    }

    @Nested
    @DisplayName("scope THIS_ONE")
    class ThisOne {

        @Test
        @DisplayName("occurrenceDate is required for THIS_ONE")
        void occurrenceDateIsRequired() {
            RecurrenceResponse rule = createMonthly(5, LocalDate.of(2026, 1, 1));

            ResponseEntity<ErrorResponse> response = session.patch(
                    path("/recurrences/" + rule.id() + "?scope=THIS_ONE"),
                    monthlyRequest(5, LocalDate.of(2026, 1, 1)),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
            assertThat(response.getBody().fieldErrors()).extracting(FieldErrorDto::field).containsExactly("occurrenceDate");
        }

        @Test
        @DisplayName("a date the rule does not fall on is RECURRENCE_OCCURRENCE_NOT_FOUND")
        void aDateOffScheduleIsNotFound() {
            RecurrenceResponse rule = createMonthly(5, LocalDate.of(2026, 1, 1));

            ResponseEntity<ErrorResponse> response = session.patch(
                    path("/recurrences/" + rule.id() + "?scope=THIS_ONE&occurrenceDate=2026-08-06"),
                    monthlyRequest(5, LocalDate.of(2026, 1, 1)),
                    ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo("RECURRENCE_OCCURRENCE_NOT_FOUND");
        }

        @Test
        @DisplayName("editing one occurrence only touches the transaction it materialized, leaving the rule alone")
        void editingOneOccurrenceTouchesOnlyThatTransaction() {
            RecurrenceResponse rule = createMonthly(5, LocalDate.of(2026, 1, 1));
            LocalDate occurrenceDate = LocalDate.of(2026, 8, 5);

            RecurrenceRequest edit = new RecurrenceRequest(
                    TransactionType.EXPENSE,
                    account.id(),
                    expenseCategory.id(),
                    150_000L,
                    "Aluguel (reajustado)",
                    "negociado com o síndico",
                    RecurrenceFrequency.MONTHLY,
                    5,
                    null,
                    null,
                    LocalDate.of(2026, 1, 1),
                    null,
                    null);

            ResponseEntity<RecurrenceResponse> response = session.patch(
                    path("/recurrences/" + rule.id() + "?scope=THIS_ONE&occurrenceDate=" + occurrenceDate),
                    edit,
                    RecurrenceResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().id()).isEqualTo(rule.id());
            assertThat(response.getBody().amountCents()).isEqualTo(120_000L); // the rule itself is untouched

            TransactionPageResponse page = session.get(
                            path("/transactions?from=2026-08-01&to=2026-08-31"), TransactionPageResponse.class)
                    .getBody();
            assertThat(page.content()).hasSize(1);
            TransactionResponse transaction = page.content().get(0);
            assertThat(transaction.amountCents()).isEqualTo(150_000L);
            assertThat(transaction.description()).isEqualTo("Aluguel (reajustado)");
            assertThat(transaction.date()).isEqualTo(occurrenceDate);
            assertThat(transaction.recurrenceId()).isEqualTo(rule.id());

            // September, untouched by the THIS_ONE edit, still materializes with the rule's own values.
            materializer.materializeMonth(workspaceId, YearMonth.of(2026, 9));
            TransactionPageResponse september = session.get(
                            path("/transactions?from=2026-09-01&to=2026-09-30"), TransactionPageResponse.class)
                    .getBody();
            assertThat(september.content()).hasSize(1);
            assertThat(september.content().get(0).amountCents()).isEqualTo(120_000L);
        }

        @Test
        @DisplayName("deleting one occurrence skips it and removes the PENDING transaction")
        void deletingOneOccurrenceSkipsItAndRemovesTheTransaction() {
            RecurrenceResponse rule = createMonthly(5, LocalDate.of(2026, 1, 1));
            LocalDate occurrenceDate = LocalDate.of(2026, 8, 5);
            materializer.materializeMonth(workspaceId, YearMonth.of(2026, 8));

            ResponseEntity<Void> response = session.delete(
                    path("/recurrences/" + rule.id() + "?scope=THIS_ONE&occurrenceDate=" + occurrenceDate),
                    Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            TransactionPageResponse page = session.get(
                            path("/transactions?from=2026-08-01&to=2026-08-31"), TransactionPageResponse.class)
                    .getBody();
            assertThat(page.content()).isEmpty();

            // Re-reading the month must not resurrect the skipped occurrence.
            materializer.materializeMonth(workspaceId, YearMonth.of(2026, 8));
            TransactionPageResponse again = session.get(
                            path("/transactions?from=2026-08-01&to=2026-08-31"), TransactionPageResponse.class)
                    .getBody();
            assertThat(again.content()).isEmpty();
        }

        @Test
        @DisplayName("a future occurrence never read yet can be skipped without ever materializing")
        void aNeverMaterializedOccurrenceCanBeSkipped() {
            RecurrenceResponse rule = createMonthly(5, LocalDate.of(2026, 1, 1));
            LocalDate occurrenceDate = LocalDate.of(2026, 8, 5);

            ResponseEntity<Void> response = session.delete(
                    path("/recurrences/" + rule.id() + "?scope=THIS_ONE&occurrenceDate=" + occurrenceDate),
                    Void.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

            materializer.materializeMonth(workspaceId, YearMonth.of(2026, 8));
            TransactionPageResponse page = session.get(
                            path("/transactions?from=2026-08-01&to=2026-08-31"), TransactionPageResponse.class)
                    .getBody();
            assertThat(page.content()).isEmpty();
        }

        @Test
        @DisplayName("deleting an already-settled occurrence is a no-op — settled money is never erased")
        void deletingASettledOccurrenceIsANoOp() {
            RecurrenceResponse rule = createMonthly(5, LocalDate.of(2026, 1, 1));
            LocalDate occurrenceDate = LocalDate.of(2026, 8, 5);
            materializer.materializeMonth(workspaceId, YearMonth.of(2026, 8));
            TransactionResponse pending = session.get(
                            path("/transactions?from=2026-08-01&to=2026-08-31"), TransactionPageResponse.class)
                    .getBody()
                    .content()
                    .get(0);
            session.post(path("/transactions/" + pending.id() + "/settle"), null, TransactionResponse.class);

            ResponseEntity<Void> response = session.delete(
                    path("/recurrences/" + rule.id() + "?scope=THIS_ONE&occurrenceDate=" + occurrenceDate),
                    Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            TransactionPageResponse page = session.get(
                            path("/transactions?from=2026-08-01&to=2026-08-31"), TransactionPageResponse.class)
                    .getBody();
            assertThat(page.content()).hasSize(1);
            assertThat(page.content().get(0).status()).isEqualTo(TransactionStatus.SETTLED);
        }
    }

    @Nested
    @DisplayName("scope THIS_AND_FUTURE")
    class ThisAndFuture {

        @Test
        @DisplayName("closes the old rule the day before the cutover and starts a new one from it")
        void closesTheOldRuleAndStartsANewOneFromTheCutover() {
            RecurrenceResponse rule = createMonthly(5, LocalDate.of(2026, 1, 1));
            LocalDate cutover = LocalDate.of(2026, 8, 5);

            RecurrenceRequest edit = monthlyRequest(20, LocalDate.of(2026, 1, 1));
            ResponseEntity<RecurrenceResponse> response = session.patch(
                    path("/recurrences/" + rule.id() + "?scope=THIS_AND_FUTURE&occurrenceDate=" + cutover),
                    edit,
                    RecurrenceResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            RecurrenceResponse newRule = response.getBody();
            assertThat(newRule.id()).isNotEqualTo(rule.id());
            assertThat(newRule.dayOfMonth()).isEqualTo(20);
            assertThat(newRule.startDate()).isEqualTo(cutover);

            RecurrenceResponse[] all = session.get(path("/recurrences"), RecurrenceResponse[].class).getBody();
            RecurrenceResponse oldRule = Arrays.stream(all)
                    .filter(r -> r.id().equals(rule.id()))
                    .findFirst()
                    .orElseThrow();
            assertThat(oldRule.endDate()).isEqualTo(cutover.minusDays(1));
            assertThat(oldRule.active()).isFalse();
        }

        @Test
        @DisplayName("past occurrences are never altered by a THIS_AND_FUTURE edit")
        void pastOccurrencesAreNeverAltered() {
            RecurrenceResponse rule = createMonthly(5, LocalDate.of(2026, 1, 1));
            materializer.materializeMonth(workspaceId, YearMonth.of(2026, 6));
            TransactionResponse juneTransaction = session.get(
                            path("/transactions?from=2026-06-01&to=2026-06-30"), TransactionPageResponse.class)
                    .getBody()
                    .content()
                    .get(0);
            session.post(path("/transactions/" + juneTransaction.id() + "/settle"), null, TransactionResponse.class);

            session.patch(
                    path("/recurrences/" + rule.id() + "?scope=THIS_AND_FUTURE&occurrenceDate=2026-08-05"),
                    monthlyRequest(20, LocalDate.of(2026, 1, 1)),
                    RecurrenceResponse.class);

            TransactionPageResponse junePage = session.get(
                            path("/transactions?from=2026-06-01&to=2026-06-30"), TransactionPageResponse.class)
                    .getBody();
            assertThat(junePage.content()).hasSize(1);
            assertThat(junePage.content().get(0).status()).isEqualTo(TransactionStatus.SETTLED);
            assertThat(junePage.content().get(0).amountCents()).isEqualTo(120_000L);
            assertThat(junePage.content().get(0).date()).isEqualTo(LocalDate.of(2026, 6, 5));
        }

        @Test
        @DisplayName("still-PENDING occurrences at or after the cutover are dropped, freeing the new rule's dates")
        void pendingOccurrencesAtOrAfterCutoverAreDropped() {
            RecurrenceResponse rule = createMonthly(5, LocalDate.of(2026, 1, 1));
            materializer.materializeMonth(workspaceId, YearMonth.of(2026, 8));

            RecurrenceResponse newRule = session.patch(
                            path("/recurrences/" + rule.id() + "?scope=THIS_AND_FUTURE&occurrenceDate=2026-08-05"),
                            monthlyRequest(20, LocalDate.of(2026, 1, 1)),
                            RecurrenceResponse.class)
                    .getBody();

            // The extrato itself materializes the window it reads (BE-8): the old rule's Aug 5th
            // occurrence must be gone, and the date it freed now belongs to the new rule's Aug 20th —
            // proving both halves of this test's name in one read, instead of only the first half.
            TransactionPageResponse augustPage = session.get(
                            path("/transactions?from=2026-08-01&to=2026-08-31"), TransactionPageResponse.class)
                    .getBody();
            assertThat(augustPage.content()).extracting(TransactionResponse::date).containsExactly(LocalDate.of(2026, 8, 20));
            assertThat(augustPage.content().get(0).recurrenceId()).isEqualTo(newRule.id());
        }

        @Test
        @DisplayName("deleting closes the series at the cutover and drops future PENDING occurrences")
        void deletingClosesTheSeriesAtTheCutover() {
            RecurrenceResponse rule = createMonthly(5, LocalDate.of(2026, 1, 1));
            materializer.materializeMonth(workspaceId, YearMonth.of(2026, 8));

            ResponseEntity<Void> response = session.delete(
                    path("/recurrences/" + rule.id() + "?scope=THIS_AND_FUTURE&occurrenceDate=2026-08-01"),
                    Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            TransactionPageResponse augustPage = session.get(
                            path("/transactions?from=2026-08-01&to=2026-08-31"), TransactionPageResponse.class)
                    .getBody();
            assertThat(augustPage.content()).isEmpty();

            materializer.materializeMonth(workspaceId, YearMonth.of(2026, 9));
            TransactionPageResponse septemberPage = session.get(
                            path("/transactions?from=2026-09-01&to=2026-09-30"), TransactionPageResponse.class)
                    .getBody();
            assertThat(septemberPage.content()).isEmpty();
        }

        @Test
        @DisplayName("unknown recurrenceId is RECURRENCE_NOT_FOUND")
        void unknownRecurrenceIsNotFound() {
            ResponseEntity<ErrorResponse> response =
                    session.delete(path("/recurrences/" + UUID.randomUUID()), ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(response.getBody().code()).isEqualTo("RECURRENCE_NOT_FOUND");
        }
    }
}
