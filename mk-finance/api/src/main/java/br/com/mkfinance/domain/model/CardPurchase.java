package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Maps {@code card_purchase} (V1__schema.sql) — what the user actually bought, as opposed to how it
 * was billed.
 *
 * <p>A card purchase is deliberately <strong>not</strong> a {@link Transaction}: it does not move
 * money in any account (R2). The account is only debited later, when the statement it landed on is
 * paid. What the purchase does carry is {@link #categoryId} and {@link #purchaseDate}, which is what
 * lets reports count the spending in the month it happened rather than in the month the bill was
 * paid — otherwise every monthly report would collapse into a single "statement payment" line.
 */
@Entity
@Table(name = "card_purchase")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CardPurchase {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "total_amount_cents", nullable = false)
    private long totalAmountCents;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Builder.Default
    @Column(name = "installment_count", nullable = false)
    private short installmentCount = 1;

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
