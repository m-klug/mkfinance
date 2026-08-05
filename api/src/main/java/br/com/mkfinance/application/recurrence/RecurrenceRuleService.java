package br.com.mkfinance.application.recurrence;

import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.application.recurrence.RecurrenceValidationException.FieldViolation;
import br.com.mkfinance.application.transaction.TransactionInput;
import br.com.mkfinance.application.transaction.TransactionService;
import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.model.Account;
import br.com.mkfinance.domain.model.Category;
import br.com.mkfinance.domain.model.CategoryType;
import br.com.mkfinance.domain.model.RecurrenceOccurrence;
import br.com.mkfinance.domain.model.RecurrenceOccurrenceStatus;
import br.com.mkfinance.domain.model.RecurrenceRule;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransactionType;
import br.com.mkfinance.domain.service.RecurrenceScheduler;
import br.com.mkfinance.infrastructure.persistence.AccountRepository;
import br.com.mkfinance.infrastructure.persistence.CategoryRepository;
import br.com.mkfinance.infrastructure.persistence.RecurrenceOccurrenceRepository;
import br.com.mkfinance.infrastructure.persistence.RecurrenceRuleRepository;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD of recurrence rules (PLANEJAMENTO §7 Fase 2), including the calendar-style scoped edit and
 * delete (CT-1): {@code THIS_ONE} touches only the transaction one occurrence materialized into,
 * {@code THIS_AND_FUTURE} closes the rule in force and starts a new one, keeping the past intact.
 *
 * <p>Occurrence-level edits/deletes are bridged to {@link TransactionService} instead of writing to
 * {@code transaction} directly, so they get the same account/category ownership and type-match
 * validation, the same statement-payment lock, and the same balance side effects any other
 * transaction edit gets.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RecurrenceRuleService {

    /** Safety rail scanning forward for "the next occurrence on/after today" (THIS_AND_FUTURE PATCH default). */
    private static final int MAX_LOOKAHEAD_MONTHS = 24;

    private final RecurrenceRuleRepository ruleRepository;
    private final RecurrenceOccurrenceRepository occurrenceRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final RecurrenceOccurrenceMaterializer materializer;
    private final BusinessClock clock;

    @Transactional(readOnly = true)
    public List<RecurrenceRule> list(UUID workspaceId, Boolean active) {
        return active == null
                ? ruleRepository.findByWorkspaceIdOrderByStartDateAsc(workspaceId)
                : ruleRepository.findByWorkspaceIdAndActiveOrderByStartDateAsc(workspaceId, active);
    }

    @Transactional(readOnly = true)
    public RecurrenceRule get(UUID workspaceId, UUID recurrenceId) {
        return ruleRepository
                .findByIdAndWorkspaceId(recurrenceId, workspaceId)
                .orElseThrow(AppException::recurrenceNotFound);
    }

    public RecurrenceRule create(UUID workspaceId, RecurrenceRuleInput input) {
        validateSchedule(input);
        Account account = ownedAccount(workspaceId, input.accountId());
        Category category = matchingCategory(workspaceId, input.categoryId(), input.type());

        RecurrenceRule rule = RecurrenceRule.builder()
                .workspaceId(workspaceId)
                .type(input.type())
                .accountId(account.getId())
                .categoryId(category.getId())
                .amountCents(input.amountCents())
                .description(input.description())
                .note(input.note())
                .frequency(input.frequency())
                .dayOfMonth(input.dayOfMonth())
                .dayOfWeek(input.dayOfWeek())
                .monthOfYear(input.monthOfYear())
                .startDate(input.startDate())
                .endDate(input.endDate())
                .active(input.active() == null || input.active())
                .build();
        return ruleRepository.save(rule);
    }

    public RecurrenceRule update(
            UUID workspaceId,
            UUID recurrenceId,
            RecurrenceScope scope,
            LocalDate occurrenceDate,
            RecurrenceRuleInput input) {
        RecurrenceRule rule = get(workspaceId, recurrenceId);
        return switch (scope) {
            case THIS_ONE -> updateThisOne(rule, requireOccurrenceDate(occurrenceDate), input);
            case THIS_AND_FUTURE -> {
                LocalDate cutover = occurrenceDate != null ? occurrenceDate : nextOccurrenceOnOrAfter(rule, clock.today());
                yield updateThisAndFuture(rule, cutover, input);
            }
        };
    }

    public void delete(UUID workspaceId, UUID recurrenceId, RecurrenceScope scope, LocalDate occurrenceDate) {
        RecurrenceRule rule = get(workspaceId, recurrenceId);
        switch (scope) {
            case THIS_ONE -> deleteThisOne(rule, requireOccurrenceDate(occurrenceDate));
            // Omitting occurrenceDate ends the series today (contract) — unlike the PATCH default,
            // which looks ahead to the next occurrence instead.
            case THIS_AND_FUTURE -> closeRule(rule, occurrenceDate != null ? occurrenceDate : clock.today());
        }
    }

    // -- THIS_ONE ---------------------------------------------------------------------------------

    private RecurrenceRule updateThisOne(RecurrenceRule rule, LocalDate occurrenceDate, RecurrenceRuleInput input) {
        requireScheduleDate(rule, occurrenceDate);
        materializer.materializeMonth(rule.getWorkspaceId(), YearMonth.from(occurrenceDate));

        RecurrenceOccurrence occurrence = occurrenceRepository
                .findByRuleIdAndOccurrenceDate(rule.getId(), occurrenceDate)
                .filter(o -> o.getStatus() == RecurrenceOccurrenceStatus.GENERATED && o.getTransactionId() != null)
                .orElseThrow(AppException::recurrenceOccurrenceNotFound);

        // Scheduling fields (frequency, dayOfMonth, dayOfWeek, monthOfYear, startDate, endDate) are
        // ignored for THIS_ONE — one occurrence has no schedule of its own, so its date stays
        // occurrenceDate; only the fields that map onto a plain transaction are applied.
        TransactionInput txInput = new TransactionInput(
                input.accountId(),
                input.categoryId(),
                input.type(),
                input.amountCents(),
                occurrenceDate,
                input.description(),
                input.note(),
                null,
                null);
        transactionService.update(rule.getWorkspaceId(), occurrence.getTransactionId(), txInput);
        return rule;
    }

    private void deleteThisOne(RecurrenceRule rule, LocalDate occurrenceDate) {
        requireScheduleDate(rule, occurrenceDate);
        Optional<RecurrenceOccurrence> existing =
                occurrenceRepository.findByRuleIdAndOccurrenceDate(rule.getId(), occurrenceDate);

        if (existing.isPresent() && existing.get().getStatus() == RecurrenceOccurrenceStatus.SKIPPED) {
            throw AppException.recurrenceOccurrenceNotFound();
        }
        if (existing.isEmpty()) {
            // Never materialized (a future date the client has not read yet) — record the skip so it
            // never gets generated later, with nothing to remove today.
            occurrenceRepository.save(RecurrenceOccurrence.builder()
                    .ruleId(rule.getId())
                    .occurrenceDate(occurrenceDate)
                    .status(RecurrenceOccurrenceStatus.SKIPPED)
                    .build());
            return;
        }

        RecurrenceOccurrence occurrence = existing.get();
        UUID transactionId = occurrence.getTransactionId();
        if (transactionId != null) {
            Transaction transaction = transactionRepository.findById(transactionId).orElse(null);
            if (transaction != null && transaction.getStatus() == TransactionStatus.SETTLED) {
                // Deleting a rule must never erase money that was actually spent (contract).
                return;
            }
        }
        occurrence.setStatus(RecurrenceOccurrenceStatus.SKIPPED);
        occurrence.setTransactionId(null);
        occurrenceRepository.save(occurrence);
        if (transactionId != null) {
            transactionService.delete(rule.getWorkspaceId(), transactionId);
        }
    }

    // -- THIS_AND_FUTURE ----------------------------------------------------------------------------

    private RecurrenceRule updateThisAndFuture(RecurrenceRule oldRule, LocalDate cutover, RecurrenceRuleInput input) {
        validateSchedule(input);
        Account account = ownedAccount(oldRule.getWorkspaceId(), input.accountId());
        Category category = matchingCategory(oldRule.getWorkspaceId(), input.categoryId(), input.type());

        closeRule(oldRule, cutover);

        RecurrenceRule newRule = RecurrenceRule.builder()
                .workspaceId(oldRule.getWorkspaceId())
                .type(input.type())
                .accountId(account.getId())
                .categoryId(category.getId())
                .amountCents(input.amountCents())
                .description(input.description())
                .note(input.note())
                .frequency(input.frequency())
                .dayOfMonth(input.dayOfMonth())
                .dayOfWeek(input.dayOfWeek())
                .monthOfYear(input.monthOfYear())
                .startDate(cutover)
                .endDate(input.endDate())
                .active(input.active() == null || input.active())
                .build();
        return ruleRepository.save(newRule);
    }

    /**
     * Ends {@code rule} the day before {@code cutover} and drops its still-PENDING occurrences from
     * there on (contract) — used by both a THIS_AND_FUTURE delete and the supersession a
     * THIS_AND_FUTURE edit performs on the rule it replaces.
     */
    private void closeRule(RecurrenceRule rule, LocalDate cutover) {
        rule.setEndDate(cutover.minusDays(1));
        rule.setActive(false);
        ruleRepository.save(rule);

        for (RecurrenceOccurrence occurrence :
                occurrenceRepository.findByRuleIdAndOccurrenceDateGreaterThanEqual(rule.getId(), cutover)) {
            UUID transactionId = occurrence.getTransactionId();
            if (transactionId == null) {
                occurrenceRepository.delete(occurrence);
                continue;
            }
            Transaction transaction = transactionRepository.findById(transactionId).orElse(null);
            if (transaction == null || transaction.getStatus() == TransactionStatus.PENDING) {
                occurrenceRepository.delete(occurrence);
                if (transaction != null) {
                    transactionRepository.delete(transaction);
                }
            }
            // A SETTLED occurrence past the cutover is left exactly as it is — closing a rule must
            // never erase money that was actually spent.
        }
    }

    // -- shared helpers -------------------------------------------------------------------------------

    private LocalDate requireOccurrenceDate(LocalDate occurrenceDate) {
        if (occurrenceDate == null) {
            throw RecurrenceValidationException.of("occurrenceDate", "REQUIRED");
        }
        return occurrenceDate;
    }

    /** Confirms {@code occurrenceDate} is a date the rule's own schedule actually falls on. */
    private void requireScheduleDate(RecurrenceRule rule, LocalDate occurrenceDate) {
        List<LocalDate> monthDates = RecurrenceScheduler.occurrencesIn(
                rule.getFrequency(),
                rule.getDayOfMonth(),
                rule.getDayOfWeek(),
                rule.getMonthOfYear(),
                rule.getStartDate(),
                rule.getEndDate(),
                YearMonth.from(occurrenceDate));
        if (!monthDates.contains(occurrenceDate)) {
            throw AppException.recurrenceOccurrenceNotFound();
        }
    }

    /** The next date {@code rule} falls on, on or after {@code from} — the THIS_AND_FUTURE PATCH default. */
    private LocalDate nextOccurrenceOnOrAfter(RecurrenceRule rule, LocalDate from) {
        YearMonth month = YearMonth.from(from);
        for (int i = 0; i <= MAX_LOOKAHEAD_MONTHS; i++, month = month.plusMonths(1)) {
            for (LocalDate date : RecurrenceScheduler.occurrencesIn(
                    rule.getFrequency(),
                    rule.getDayOfMonth(),
                    rule.getDayOfWeek(),
                    rule.getMonthOfYear(),
                    rule.getStartDate(),
                    rule.getEndDate(),
                    month)) {
                if (!date.isBefore(from)) {
                    return date;
                }
            }
        }
        throw AppException.recurrenceOccurrenceNotFound();
    }

    /** CT-1: which scheduling field is required depends on {@code frequency}; OpenAPI cannot express it. */
    private void validateSchedule(RecurrenceRuleInput input) {
        List<FieldViolation> violations = new ArrayList<>();
        if (input.type() != TransactionType.INCOME && input.type() != TransactionType.EXPENSE) {
            violations.add(new FieldViolation("type", "REQUIRED"));
        }
        switch (input.frequency()) {
            case MONTHLY -> {
                if (input.dayOfMonth() == null) {
                    violations.add(new FieldViolation("dayOfMonth", "REQUIRED"));
                }
            }
            case WEEKLY -> {
                if (input.dayOfWeek() == null) {
                    violations.add(new FieldViolation("dayOfWeek", "REQUIRED"));
                }
            }
            case YEARLY -> {
                if (input.dayOfMonth() == null) {
                    violations.add(new FieldViolation("dayOfMonth", "REQUIRED"));
                }
                if (input.monthOfYear() == null) {
                    violations.add(new FieldViolation("monthOfYear", "REQUIRED"));
                }
            }
        }
        if (!violations.isEmpty()) {
            throw new RecurrenceValidationException(violations);
        }
    }

    private Account ownedAccount(UUID workspaceId, UUID accountId) {
        if (accountId == null) {
            throw AppException.accountNotFound();
        }
        return accountRepository
                .findByIdAndWorkspaceId(accountId, workspaceId)
                .orElseThrow(AppException::accountNotFound);
    }

    private Category matchingCategory(UUID workspaceId, UUID categoryId, TransactionType type) {
        if (categoryId == null) {
            throw AppException.categoryNotFound();
        }
        Category category = categoryRepository
                .findByIdAndWorkspaceId(categoryId, workspaceId)
                .orElseThrow(AppException::categoryNotFound);
        CategoryType expected = type == TransactionType.INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
        if (category.getType() != expected) {
            throw AppException.categoryTypeMismatch();
        }
        return category;
    }
}
