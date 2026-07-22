package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Maps {@code account} (V1__schema.sql). {@code workspaceId} is kept as a plain scalar column
 * (not a JPA relationship) so every repository query filters by it directly (PLANEJAMENTO §5 R8).
 * Balance is computed by BE-3 (BalanceCalculator) — not stored here.
 */
@Entity
@Table(name = "account")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AccountType type;

    @Builder.Default
    @Column(name = "initial_balance_cents", nullable = false)
    private long initialBalanceCents = 0L;

    /** Only meaningful for INVESTMENT/SAVINGS; only GOALS/FINANCIAL_FREEDOM allowed (R9). */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_contribution_pillar", length = 20)
    private Pillar defaultContributionPillar;

    @Column(length = 7)
    private String color;

    @Column(length = 40)
    private String icon;

    @Builder.Default
    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
