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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Maps {@code vault} (V18__vault.sql) — a virtual subdivision of an INVESTMENT/SAVINGS account's
 * balance (DESENVOLVIMENTO-CAIXINHAS.md §0/§4). Like every other entity here, {@link #accountId}
 * is a plain scalar column rather than a JPA relationship (PLANEJAMENTO §5 R8) so every query
 * filters by {@code workspaceId} explicitly.
 *
 * <p>The vault never stores its own balance — {@code domain/service/VaultBalanceCalculator} derives
 * it from {@link VaultEntry} the same way {@code BalanceCalculator} derives an account's. A goal
 * ({@link #targetAmountCents}/{@link #targetDate}) is optional and computed into
 * {@code domain/service/VaultGoalCalculator}'s report, never stored pre-computed.
 */
@Entity
@Table(name = "vault")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vault {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, length = 120)
    private String name;

    /** The pillar a contribution into this vault consumes (R9) — DESENVOLVIMENTO-CAIXINHAS.md §4.4. */
    @Enumerated(EnumType.STRING)
    @Column(name = "contribution_pillar", nullable = false, length = 20)
    private ContributionPillar contributionPillar;

    /** Null means the vault just holds money, with nothing to reach. */
    @Column(name = "target_amount_cents")
    private Long targetAmountCents;

    /** Only meaningful with {@link #targetAmountCents} (DB check {@code ck_vault_goal}). */
    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(length = 7)
    private String color;

    @Builder.Default
    @Column(nullable = false)
    private boolean archived = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** Present exactly when {@link #targetAmountCents} is set. */
    public boolean hasGoal() {
        return targetAmountCents != null;
    }

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
