package br.com.mkfinance.application.investment;

import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.infrastructure.persistence.FixedIncomePositionRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Asked by the transaction services before they change or drop a row a position depends on, so the
 * client gets TRANSACTION_HAS_POSITION — which names the way out — instead of the foreign key's
 * opaque DATA_INTEGRITY_VIOLATION, or worse, a position whose copied principal no longer matches
 * its purchase. Same idea as the reimbursement and statement-payment guards.
 */
@Component
@RequiredArgsConstructor
public class FixedIncomePositionGuard {

    private final FixedIncomePositionRepository repository;

    /** The row is a position's purchase, its booked yield or its return transfer. */
    public boolean isReferenced(UUID transactionId) {
        return repository.existsReferencing(transactionId);
    }

    /** The row is the purchase of a position, whose amount, date and account were copied from it. */
    public boolean isContribution(UUID transactionId) {
        return repository.existsByContributionTransactionId(transactionId);
    }

    /** @throws AppException TRANSACTION_HAS_POSITION */
    public void assertNotReferenced(UUID transactionId) {
        if (isReferenced(transactionId)) {
            throw AppException.transactionHasPosition();
        }
    }
}
