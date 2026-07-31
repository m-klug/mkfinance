package br.com.mkfinance.infrastructure.persistence;

import br.com.mkfinance.domain.model.PillarAllocation;
import br.com.mkfinance.domain.model.PillarAllocationId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PillarAllocationRepository extends JpaRepository<PillarAllocation, PillarAllocationId> {

    /**
     * The targets in force for a competency (BE-6, V13): per pillar, the row whose
     * {@code effectiveFrom} is the latest one not after {@code asOf} (typically a month's first day).
     * Correlated subquery rather than a window function so it stays plain JPQL — six pillars per
     * workspace keeps the cost trivial.
     */
    @Query(
            """
            select pa from PillarAllocation pa
            where pa.id.workspaceId = :workspaceId
              and pa.id.effectiveFrom = (
                  select max(pa2.id.effectiveFrom)
                  from PillarAllocation pa2
                  where pa2.id.workspaceId = pa.id.workspaceId
                    and pa2.id.pillar = pa.id.pillar
                    and pa2.id.effectiveFrom <= :asOf
              )
            order by pa.id.pillar
            """)
    List<PillarAllocation> findEffectiveAt(@Param("workspaceId") UUID workspaceId, @Param("asOf") LocalDate asOf);
}
