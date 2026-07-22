package br.com.mkfinance.application.transaction;

import br.com.mkfinance.domain.model.Transaction;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds the extrato query. The workspace predicate is not optional — it is always added first, so
 * no listing can escape workspace isolation (R8) by forgetting a filter.
 */
final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    static Specification<Transaction> of(UUID workspaceId, TransactionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("workspaceId"), workspaceId));

            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("date"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("date"), filter.to()));
            }
            if (filter.accountId() != null) {
                predicates.add(cb.equal(root.get("accountId"), filter.accountId()));
            }
            if (filter.categoryId() != null) {
                predicates.add(cb.equal(root.get("categoryId"), filter.categoryId()));
            }
            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.search() != null && !filter.search().isBlank()) {
                String pattern = "%" + filter.search().trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("note")), pattern)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
