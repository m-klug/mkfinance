package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Maps {@code tag} (V16__tags.sql, BE-8). A tag is nothing but its {@link #name}, unique per
 * workspace case-insensitively ({@code ux_tag_workspace_name_ci}) — the DB index is the source of
 * truth for uniqueness, {@code application/tag/TagService} only checks first to answer a clean
 * TAG_NAME_DUPLICATE instead of letting the unique-index violation surface as a raw
 * DATA_INTEGRITY_VIOLATION.
 *
 * <p>Like every other entity here, no JPA relationship connects it to {@link Transaction} — see
 * {@code TransactionTag} for the join table, addressed by plain UUID columns.
 */
@Entity
@Table(name = "tag")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tag {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
