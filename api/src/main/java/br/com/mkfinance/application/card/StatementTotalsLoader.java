package br.com.mkfinance.application.card;

import br.com.mkfinance.domain.model.Statement;
import br.com.mkfinance.domain.model.StatementStatus;
import br.com.mkfinance.infrastructure.persistence.AmountByStatement;
import br.com.mkfinance.infrastructure.persistence.InstallmentRepository;
import br.com.mkfinance.infrastructure.persistence.StatementPaymentRepository;
import br.com.mkfinance.infrastructure.persistence.StatementRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves {@link StatementTotals} for whole batches of statements, and keeps the persisted status
 * in step with them.
 *
 * <p>Totals are aggregated in the database (two grouped queries for any number of statements) rather
 * than by walking installments in memory: a card statement list covers a year at a time, and the
 * naive version would issue two queries per row.
 */
@Component
@RequiredArgsConstructor
public class StatementTotalsLoader {

    private final InstallmentRepository installmentRepository;
    private final StatementPaymentRepository statementPaymentRepository;
    private final StatementRepository statementRepository;

    /** Totals for every given statement; statements with no installments map to {@link StatementTotals#EMPTY}. */
    @Transactional(readOnly = true)
    public Map<UUID, StatementTotals> load(Collection<UUID> statementIds) {
        if (statementIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Long> totals = sumsById(installmentRepository.sumByStatementIds(statementIds));
        Map<UUID, Long> paid = sumsById(statementPaymentRepository.sumByStatementIds(statementIds));

        Map<UUID, StatementTotals> result = new HashMap<>(statementIds.size());
        for (UUID id : statementIds) {
            result.put(id, new StatementTotals(totals.getOrDefault(id, 0L), paid.getOrDefault(id, 0L)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public StatementTotals load(UUID statementId) {
        return load(List.of(statementId)).getOrDefault(statementId, StatementTotals.EMPTY);
    }

    /**
     * Recomputes and persists a statement's payment status after something changed its total or its
     * payments — adding an installment to a partially paid statement can push it back from PARTIAL,
     * and removing one can complete it.
     *
     * <p>Called on every path that touches installments or payments, so the stored status is a cache
     * of the derived truth rather than an independent source of it.
     */
    @Transactional
    public StatementTotals refreshStatus(Statement statement) {
        StatementTotals totals = load(statement.getId());
        StatementStatus recomputed = totals.paymentStatus();
        if (statement.getStatus() != recomputed) {
            statement.setStatus(recomputed);
            statementRepository.save(statement);
        }
        return totals;
    }

    private Map<UUID, Long> sumsById(List<AmountByStatement> rows) {
        return rows.stream()
                .collect(Collectors.toMap(
                        AmountByStatement::statementId, AmountByStatement::amountCents, Long::sum));
    }

    /** Convenience for callers that already hold the statements rather than their ids. */
    public Map<UUID, StatementTotals> loadFor(Collection<Statement> statements) {
        return load(statements.stream().map(Statement::getId).collect(Collectors.toSet()));
    }
}
