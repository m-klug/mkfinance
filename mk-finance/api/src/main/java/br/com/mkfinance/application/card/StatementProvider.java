package br.com.mkfinance.application.card;

import br.com.mkfinance.domain.model.Card;
import br.com.mkfinance.domain.model.Statement;
import br.com.mkfinance.domain.service.StatementAllocator;
import br.com.mkfinance.domain.service.StatementAllocator.StatementSchedule;
import br.com.mkfinance.infrastructure.persistence.StatementRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializes statements on demand, one per {@code (card, reference month)} (R3).
 *
 * <p>Statements are not generated ahead of time by a scheduled job. A card that has existed for
 * three years would otherwise carry three years of empty rows, and a 24× purchase would need
 * statements two years out that the job had not created yet. Instead a statement row appears the
 * first time something actually refers to that month — an installment landing on it, or the user
 * opening the month in the UI.
 *
 * <p>The closing/due dates are frozen into the row at creation time, taken from the card's current
 * {@code closingDay}/{@code dueDay}. So editing those days reshapes future statements while leaving
 * already-materialized ones alone, which is what the contract promises ("only affects OPEN/future
 * statements") and what keeps historical bills faithful to how they were actually billed.
 */
@Component
@RequiredArgsConstructor
public class StatementProvider {

    /** Safety rail for range materialization; 60 installments is the contract's ceiling for a purchase. */
    private static final int MAX_MATERIALIZED_MONTHS = 120;

    private final StatementRepository statementRepository;

    /** The statement of {@code card} for {@code referenceMonth}, created if this is its first use. */
    @Transactional
    public Statement statementOf(Card card, YearMonth referenceMonth) {
        LocalDate monthStart = referenceMonth.atDay(1);
        return statementRepository
                .findByCardIdAndReferenceMonth(card.getId(), monthStart)
                .orElseGet(() -> create(card, referenceMonth));
    }

    /**
     * The statements of {@code card} for every month in {@code [from, to]}, creating the missing
     * ones — the listing path, where the user expects to be able to page into a month that has had
     * no activity yet.
     */
    @Transactional
    public List<Statement> statementsIn(Card card, YearMonth from, YearMonth to) {
        if (to.isBefore(from)) {
            return List.of();
        }
        Map<YearMonth, Statement> existing = new LinkedHashMap<>();
        statementRepository
                .findByCardIdAndReferenceMonthBetweenOrderByReferenceMonthAsc(
                        card.getId(), from.atDay(1), to.atDay(1))
                .forEach(statement -> existing.put(statement.referenceYearMonth(), statement));

        List<Statement> result = new ArrayList<>();
        YearMonth month = from;
        for (int i = 0; i <= MAX_MATERIALIZED_MONTHS && !month.isAfter(to); i++, month = month.plusMonths(1)) {
            Statement statement = existing.get(month);
            result.add(statement != null ? statement : create(card, month));
        }
        return result;
    }

    /** Every statement the card already has, without creating anything. */
    @Transactional(readOnly = true)
    public List<Statement> existingStatements(Card card) {
        return statementRepository.findByCardIdOrderByReferenceMonthAsc(card.getId());
    }

    private Statement create(Card card, YearMonth referenceMonth) {
        StatementSchedule schedule =
                StatementAllocator.scheduleFor(referenceMonth, card.getClosingDay(), card.getDueDay());
        Statement statement = Statement.builder()
                .cardId(card.getId())
                .referenceMonth(referenceMonth.atDay(1))
                .closingDate(schedule.closingDate())
                .dueDate(schedule.dueDate())
                .build();
        try {
            return statementRepository.saveAndFlush(statement);
        } catch (DataIntegrityViolationException ex) {
            // Two concurrent requests raced to materialize the same month; uq_statement_card_month
            // let exactly one win, so adopt the row that already exists instead of failing the user.
            return statementRepository
                    .findByCardIdAndReferenceMonth(card.getId(), referenceMonth.atDay(1))
                    .orElseThrow(() -> ex);
        }
    }
}
