package br.com.mkfinance.application.calendar;

import br.com.mkfinance.application.card.StatementTotals;
import br.com.mkfinance.application.card.StatementTotalsLoader;
import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.application.recurrence.RecurrenceOccurrenceMaterializer;
import br.com.mkfinance.domain.model.Card;
import br.com.mkfinance.domain.model.Statement;
import br.com.mkfinance.domain.model.Transaction;
import br.com.mkfinance.domain.model.TransactionStatus;
import br.com.mkfinance.domain.model.TransferDirection;
import br.com.mkfinance.infrastructure.persistence.CardRepository;
import br.com.mkfinance.infrastructure.persistence.StatementRepository;
import br.com.mkfinance.infrastructure.persistence.TransactionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read-only month agenda (contract tag {@code calendar}): statement due dates and every
 * still-pending transaction, including the ones a recurrence materialized — reading the month is
 * what {@link RecurrenceOccurrenceMaterializer} uses to lazily create them (CT-1). There is no
 * separate "recurrence occurrence" entry kind: a materialized occurrence <em>is</em> a pending
 * transaction (it carries {@code recurrenceId}), so listing it twice here would double the month.
 * Nothing is confirmed or paid from here — settling still goes through
 * {@code POST /transactions/{transactionId}/settle}.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CalendarService {

    private final RecurrenceOccurrenceMaterializer materializer;
    private final TransactionRepository transactionRepository;
    private final StatementRepository statementRepository;
    private final StatementTotalsLoader statementTotalsLoader;
    private final CardRepository cardRepository;
    private final BusinessClock clock;

    /** The requested month, or the current one in America/Sao_Paulo when the client omits it (R7). */
    public YearMonth resolveMonth(YearMonth month) {
        return month != null ? month : YearMonth.from(clock.today());
    }

    /** Entries of {@code month}, ascending by date — materializing the month's recurrences first. */
    public List<CalendarEntryView> entriesOf(UUID workspaceId, YearMonth month) {
        materializer.materializeMonth(workspaceId, month);

        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        List<CalendarEntryView> entries = new ArrayList<>();
        entries.addAll(statementDueEntries(workspaceId, from, to));
        entries.addAll(pendingTransactionEntries(workspaceId, from, to));
        entries.sort(Comparator.comparing(CalendarEntryView::date).thenComparing(CalendarEntryView::description));
        return entries;
    }

    /** Bills across the workspace's cards due in the month that still owe something (R2/R3). */
    private List<CalendarEntryView> statementDueEntries(UUID workspaceId, LocalDate from, LocalDate to) {
        List<Statement> statements = statementRepository.findByWorkspaceIdAndDueDateBetween(workspaceId, from, to);
        if (statements.isEmpty()) {
            return List.of();
        }
        Map<UUID, StatementTotals> totals = statementTotalsLoader.loadFor(statements);
        Map<UUID, Card> cardsById = cardRepository.findByWorkspaceIdOrderByNameAsc(workspaceId).stream()
                .collect(Collectors.toMap(Card::getId, card -> card));

        List<CalendarEntryView> views = new ArrayList<>();
        for (Statement statement : statements) {
            StatementTotals total = totals.getOrDefault(statement.getId(), StatementTotals.EMPTY);
            if (total.totalCents() <= 0 || total.remainingCents() <= 0) {
                continue; // nothing owed — a paid or empty statement has no place on the agenda
            }
            Card card = cardsById.get(statement.getCardId());
            views.add(new CalendarEntryView(
                    statement.getDueDate(),
                    CalendarEntryKind.STATEMENT_DUE,
                    card != null ? card.getName() : "",
                    total.remainingCents(),
                    null,
                    null,
                    statement.getId(),
                    statement.getCardId(),
                    null,
                    null,
                    null));
        }
        return views;
    }

    /** Every scheduled entry of the month, including the ones a recurrence just materialized (R5). */
    private List<CalendarEntryView> pendingTransactionEntries(UUID workspaceId, LocalDate from, LocalDate to) {
        List<CalendarEntryView> views = new ArrayList<>();
        for (Transaction transaction : transactionRepository.findByWorkspaceIdAndStatusAndDateBetweenOrderByDateAsc(
                workspaceId, TransactionStatus.PENDING, from, to)) {
            views.add(new CalendarEntryView(
                    transaction.getDate(),
                    CalendarEntryKind.PENDING_TRANSACTION,
                    transaction.getDescription(),
                    transaction.getAmountCents(),
                    direction(transaction),
                    transaction.getId(),
                    null,
                    null,
                    transaction.getAccountId(),
                    transaction.getCategoryId(),
                    transaction.getRecurrenceId()));
        }
        return views;
    }

    private TransferDirection direction(Transaction transaction) {
        return switch (transaction.getType()) {
            case INCOME -> TransferDirection.IN;
            case EXPENSE -> TransferDirection.OUT;
            case TRANSFER -> transaction.getTransferDirection();
        };
    }
}
