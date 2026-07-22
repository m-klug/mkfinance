package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.PillarAllocation;
import br.com.mkfinance.domain.model.PillarAllocationId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PillarAllocationRepository extends JpaRepository<PillarAllocation, PillarAllocationId> {

    @Query("select pa from PillarAllocation pa where pa.id.workspaceId = :workspaceId order by pa.id.pillar")
    List<PillarAllocation> findByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
