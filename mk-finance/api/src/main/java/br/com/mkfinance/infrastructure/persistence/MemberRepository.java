package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.Member;
import br.com.mkfinance.domain.model.MemberId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, MemberId> {

    List<Member> findByIdWorkspaceId(UUID workspaceId);

    @Query("select m from Member m join fetch m.workspace where m.id.userId = :userId order by m.workspace.name")
    List<Member> findByUserIdWithWorkspace(@Param("userId") UUID userId);
}
