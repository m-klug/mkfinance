package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps {@code pillar_allocation} (V1__schema.sql, versioned by competency since V13). One row per
 * (workspace, pillar, effectiveFrom): the targets in force for a month are the ones with the
 * greatest {@code effectiveFrom} not after that month's first day (PLANEJAMENTO §6.4/§6.6, R10),
 * resolved by {@code PillarAllocationRepository.findEffectiveAt}. Editing (PUT
 * {@code /pillar-allocations}) inserts a new row at the current month's first day rather than
 * overwriting one, so a past report keeps the percentages it was actually judged against.
 *
 * <p>{@code WorkspaceDefaultsSeeder} seeds a fresh workspace with the fixed MVP targets — FIXED_COSTS
 * 30, COMFORT 10, GOALS 20, PLEASURES 10, FINANCIAL_FREEDOM 25, KNOWLEDGE 5 (sums to 100) — dated
 * {@link #FLOOR_DATE}, so they read as in force since always.
 */
@Entity
@Table(name = "pillar_allocation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PillarAllocation {

    /**
     * Floor date used to backfill pre-V13 rows and to seed a new workspace's initial targets — well
     * before the app existed, so it reads as "in force since always" for any competency ever queried.
     */
    public static final LocalDate FLOOR_DATE = LocalDate.of(2000, 1, 1);

    @EmbeddedId
    private PillarAllocationId id;

    @Column(name = "target_percent", nullable = false)
    private short targetPercent;
}
