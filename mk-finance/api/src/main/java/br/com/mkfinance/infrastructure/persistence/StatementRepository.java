package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.Statement;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code statement} has no {@code workspace_id} column — a statement belongs to a card, and the card
 * belongs to the workspace. Rather than denormalize the column (and risk the two disagreeing), the
 * workspace-scoped lookups here join {@code Card} explicitly, so R8 still holds on every path that
 * reaches a statement by id.
 */
public interface StatementRepository extends JpaRepository<Statement, UUID> {

    Optional<Statement> findByCardIdAndReferenceMonth(UUID cardId, LocalDate referenceMonth);

    List<Statement> findByCardIdOrderByReferenceMonthAsc(UUID cardId);

    List<Statement> findByCardIdAndReferenceMonthBetweenOrderByReferenceMonthAsc(
            UUID cardId, LocalDate from, LocalDate to);

    List<Statement> findByCardIdIn(Collection<UUID> cardIds);

    /** The isolation-safe way to load a statement addressed by id from an API path (R8). */
    @Query("""
            select s from Statement s
            where s.id = :id
              and s.cardId in (select c.id from Card c where c.workspaceId = :workspaceId)
            """)
    Optional<Statement> findByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId);

    /** Statements of the workspace due within {@code [from, to]} — the calendar's STATEMENT_DUE entries. */
    @Query("""
            select s from Statement s
            where s.dueDate between :from and :to
              and s.cardId in (select c.id from Card c where c.workspaceId = :workspaceId)
            order by s.dueDate asc
            """)
    List<Statement> findByWorkspaceIdAndDueDateBetween(
            @Param("workspaceId") UUID workspaceId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
