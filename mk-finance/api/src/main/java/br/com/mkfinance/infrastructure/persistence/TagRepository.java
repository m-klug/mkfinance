package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.Tag;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    List<Tag> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    Optional<Tag> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

    Optional<Tag> findByWorkspaceIdAndNameIgnoreCase(UUID workspaceId, String name);

    /**
     * Case-insensitive bulk lookup by name, used to resolve {@code TransactionRequest.tags} (names,
     * not ids — CT-1) and the {@code tags} extrato filter without one query per name. {@code
     * lowerNames} must already be lower-cased by the caller; comparing with {@code lower(t.name)}
     * here is what makes the match case-insensitive, mirroring {@code ux_tag_workspace_name_ci}.
     */
    @Query("select t from Tag t where t.workspaceId = :workspaceId and lower(t.name) in :lowerNames")
    List<Tag> findByWorkspaceIdAndNameLowerIn(
            @Param("workspaceId") UUID workspaceId, @Param("lowerNames") Collection<String> lowerNames);
}
