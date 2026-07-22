package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.Card;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardRepository extends JpaRepository<Card, UUID> {

    List<Card> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    List<Card> findByWorkspaceIdAndArchivedFalseOrderByNameAsc(UUID workspaceId);

    Optional<Card> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
