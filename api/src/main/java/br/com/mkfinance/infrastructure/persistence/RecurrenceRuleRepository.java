package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.RecurrenceRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, UUID> {

    Optional<RecurrenceRule> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<RecurrenceRule> findByWorkspaceIdOrderByStartDateAsc(UUID workspaceId);

    List<RecurrenceRule> findByWorkspaceIdAndActiveOrderByStartDateAsc(UUID workspaceId, boolean active);

    /** The rules the lazy materializer walks when a competency is read — closed/deactivated ones produce nothing. */
    List<RecurrenceRule> findByWorkspaceIdAndActiveTrue(UUID workspaceId);
}
