package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps {@code pillar_allocation} (V1__schema.sql). Fixed MVP targets seeded by
 * {@code WorkspaceDefaultsSeeder}: FIXED_COSTS 30, COMFORT 10, GOALS 20, PLEASURES 10,
 * FINANCIAL_FREEDOM 25, KNOWLEDGE 5 (sums to 100). Editable targets are Phase 2.
 */
@Entity
@Table(name = "pillar_allocation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PillarAllocation {

    @EmbeddedId
    private PillarAllocationId id;

    @Column(name = "target_percent", nullable = false)
    private short targetPercent;
}
