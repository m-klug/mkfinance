package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.Installment;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InstallmentRepository extends JpaRepository<Installment, UUID> {

    List<Installment> findByPurchaseIdOrderByNumberAsc(UUID purchaseId);

    List<Installment> findByStatementId(UUID statementId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Installment i where i.purchaseId = :purchaseId")
    void deleteByPurchaseId(@Param("purchaseId") UUID purchaseId);

    /** A statement's total is the sum of the installments allocated to it — never a stored copy. */
    @Query("""
            select new br.com.mkfinance.infrastructure.persistence.AmountByStatement(
                       i.statementId, coalesce(sum(i.amountCents), 0L))
            from Installment i
            where i.statementId in :statementIds
            group by i.statementId
            """)
    List<AmountByStatement> sumByStatementIds(@Param("statementIds") Collection<UUID> statementIds);

    /** Line items of a statement, joined to their purchase (contract: StatementDetail.items). */
    @Query("""
            select new br.com.mkfinance.infrastructure.persistence.StatementItemRow(
                       i.id, p.id, p.description, p.categoryId, i.amountCents,
                       p.purchaseDate, i.number, p.installmentCount)
            from Installment i, CardPurchase p
            where i.purchaseId = p.id and i.statementId = :statementId
            order by p.purchaseDate asc, p.description asc, i.number asc
            """)
    List<StatementItemRow> findItemsOfStatement(@Param("statementId") UUID statementId);
}
