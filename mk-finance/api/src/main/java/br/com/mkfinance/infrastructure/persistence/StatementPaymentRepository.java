package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.StatementPayment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatementPaymentRepository extends JpaRepository<StatementPayment, UUID> {

    List<StatementPayment> findByStatementIdOrderByPaymentDateAsc(UUID statementId);

    /** How much of each statement is already settled — several partial payments simply add up. */
    @Query("""
            select new br.com.mkfinance.infrastructure.persistence.AmountByStatement(
                       p.statementId, coalesce(sum(p.amountCents), 0L))
            from StatementPayment p
            where p.statementId in :statementIds
            group by p.statementId
            """)
    List<AmountByStatement> sumByStatementIds(@Param("statementIds") Collection<UUID> statementIds);
}
