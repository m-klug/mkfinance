package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
 * Maps {@code category} (V1__schema.sql). EXPENSE requires a pillar, INCOME forbids one — a DB
 * check constraint ({@code ck_category_pillar}) enforces this too, but
 * {@code application/category/CategoryService} validates first to return a clean machine code
 * (CATEGORY_PILLAR_REQUIRED / CATEGORY_PILLAR_FORBIDDEN) instead of a DB error.
 */
@Entity
@Table(name = "category")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CategoryType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Pillar pillar;

    @Column(length = 7)
    private String color;

    @Column(length = 40)
    private String icon;

    /** System categories (e.g. future "statement payment") cannot be edited/deleted (contract). */
    @Builder.Default
    @Column(nullable = false)
    private boolean system = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
