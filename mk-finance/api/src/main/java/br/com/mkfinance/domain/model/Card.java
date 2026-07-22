package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Maps {@code card} (V1__schema.sql). Foreign keys are plain scalar columns, as in {@link Account}
 * and {@link Transaction}, so every query filters by {@code workspaceId} explicitly (R8).
 *
 * <p>{@link #closingDay} and {@link #dueDay} are capped at 28 by a DB check constraint and by the
 * contract: that cap is what lets {@code StatementAllocator} place a statement in <em>any</em> month
 * — February included — without inventing a "last valid day" rule users would find surprising.
 */
@Entity
@Table(name = "card")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Card {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 20)
    private String brand;

    @Column(name = "limit_cents", nullable = false)
    private long limitCents;

    @Column(name = "closing_day", nullable = false)
    private short closingDay;

    @Column(name = "due_day", nullable = false)
    private short dueDay;

    @Column(name = "default_payment_account_id")
    private UUID defaultPaymentAccountId;

    @Column(length = 7)
    private String color;

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
