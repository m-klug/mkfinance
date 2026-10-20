package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.CardPurchase;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardPurchaseRepository extends JpaRepository<CardPurchase, UUID> {

    Optional<CardPurchase> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<CardPurchase> findByCardIdAndWorkspaceIdOrderByPurchaseDateDesc(UUID cardId, UUID workspaceId);

    /** Most recent purchase whose description starts with {@code prefix}, used to suggest a category. */
    Optional<CardPurchase> findFirstByWorkspaceIdAndDescriptionStartingWithIgnoreCaseOrderByPurchaseDateDesc(
            UUID workspaceId, String prefix);
}
