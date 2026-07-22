package br.com.mkfinance.application.card;

import br.com.mkfinance.domain.model.Statement;
import br.com.mkfinance.domain.model.StatementPayment;
import br.com.mkfinance.domain.model.StatementStatus;
import br.com.mkfinance.infrastructure.persistence.StatementItemRow;
import java.util.List;

/**
 * A statement as the API presents it: the row plus the two derived numbers (total, paid) and the
 * status the client should see — CLOSED included, which the row itself never stores.
 *
 * <p>Resolving these in the application layer keeps the API mapper free of business decisions and
 * stops each response field from triggering its own query.
 */
public record StatementView(Statement statement, StatementStatus status, long totalCents, long paidCents) {

    /**
     * A statement together with its line items and the payments made against it (contract:
     * {@code StatementDetail}).
     *
     * <p>The payments are listed, not just summed into {@code paidCents}, because each one is
     * individually reversible — without their ids the client can show that a bill was paid but has
     * no way to name <em>which</em> payment to undo.
     */
    public record Detail(StatementView summary, List<StatementItemRow> items, List<StatementPayment> payments) {
    }
}
