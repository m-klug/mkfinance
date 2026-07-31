package br.com.mkfinance.application.recurrence;

import br.com.mkfinance.domain.model.RecurrenceOccurrence;
import br.com.mkfinance.domain.model.RecurrenceOccurrenceStatus;
import br.com.mkfinance.domain.model.RecurrenceRule;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.service.RecurrenceScheduler;
import br.com.mkfinance.infrastructure.persistence.RecurrenceOccurrenceRepository;
import br.com.mkfinance.infrastructure.persistence.RecurrenceRuleRepository;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializes recurrence occurrences on demand, one PENDING {@link Transaction} per
 * {@code (rule, occurrence_date)} — the same lazy-per-competency pattern
 * {@code application/card/StatementProvider} uses for statements, so a rule with no end date never
 * writes rows for a month nobody has read yet (contract, {@code createRecurrence}).
 *
 * <p>Called from both surfaces that read a month: the calendar
 * ({@code application/calendar/CalendarService}) and the occurrence-scoped edit/delete of
 * {@link RecurrenceRuleService} (a {@code THIS_ONE} edit needs the transaction the occurrence
 * materialized into to exist first).
 *
 * <p>The {@link RecurrenceOccurrence} row is inserted <em>before</em> the {@link Transaction} it
 * represents, and the unique constraint on {@code (rule_id, occurrence_date)} is what two
 * concurrent reads of the same month race on: the loser's caught
 * {@link DataIntegrityViolationException} means "already handled", never a duplicate transaction —
 * mirroring how {@code StatementProvider} lets exactly one writer win a materialization race, except
 * here the money-bearing row is only created *after* the safety check passes.
 */
@Component
@RequiredArgsConstructor
public class RecurrenceOccurrenceMaterializer {

    private final RecurrenceRuleRepository ruleRepository;
    private final RecurrenceOccurrenceRepository occurrenceRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Materializes every still-missing occurrence of every active rule of the workspace for
     * {@code month}. Idempotent — calling it twice for the same month never duplicates a
     * transaction (BE-7 aceite).
     */
    @Transactional
    public void materializeMonth(UUID workspaceId, YearMonth month) {
        for (RecurrenceRule rule : ruleRepository.findByWorkspaceIdAndActiveTrue(workspaceId)) {
            materializeRuleMonth(rule, month);
        }
    }

    private void materializeRuleMonth(RecurrenceRule rule, YearMonth month) {
        List<LocalDate> occurrenceDates = RecurrenceScheduler.occurrencesIn(
                rule.getFrequency(),
                rule.getDayOfMonth(),
                rule.getDayOfWeek(),
                rule.getMonthOfYear(),
                rule.getStartDate(),
                rule.getEndDate(),
                month);
        if (occurrenceDates.isEmpty()) {
            return;
        }

        Set<LocalDate> already = occurrenceRepository.findByRuleIdAndOccurrenceDateIn(rule.getId(), occurrenceDates)
                .stream()
                .map(RecurrenceOccurrence::getOccurrenceDate)
                .collect(Collectors.toSet());

        for (LocalDate date : occurrenceDates) {
            if (!already.contains(date)) {
                materializeOccurrence(rule, date);
            }
        }
    }

    private void materializeOccurrence(RecurrenceRule rule, LocalDate date) {
        RecurrenceOccurrence occurrence = RecurrenceOccurrence.builder()
                .ruleId(rule.getId())
                .occurrenceDate(date)
                .status(RecurrenceOccurrenceStatus.GENERATED)
                .build();
        try {
            occurrenceRepository.saveAndFlush(occurrence);
        } catch (DataIntegrityViolationException raced) {
            // Another request materialized (or skipped) this date first; nothing left to do.
            return;
        }

        Transaction transaction = Transaction.builder()
                .workspaceId(rule.getWorkspaceId())
                .accountId(rule.getAccountId())
                .categoryId(rule.getCategoryId())
                .type(rule.getType())
                .amountCents(rule.getAmountCents())
                .date(date)
                .description(rule.getDescription())
                .note(rule.getNote())
                .status(TransactionStatus.PENDING)
                .recurrenceId(rule.getId())
                .build();
        transaction = transactionRepository.save(transaction);

        occurrence.setTransactionId(transaction.getId());
        occurrenceRepository.save(occurrence);
    }
}
