package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
 * Maps {@code recurrence_occurrence} (V15__recurrence_occurrences.sql) — one row per
 * {@code (rule, occurrence_date)}, unique. This is what guarantees materializing the same
 * competency twice is a no-op (idempotency, aceite BE-7) and lets an occurrence be marked
 * {@link RecurrenceOccurrenceStatus#SKIPPED} ("pular esta ocorrência") even when no transaction was
 * ever generated for it.
 */
@Entity
@Table(name = "recurrence_occurrence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurrenceOccurrence {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "rule_id", nullable = false)
    private UUID ruleId;

    @Column(name = "occurrence_date", nullable = false)
    private LocalDate occurrenceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private RecurrenceOccurrenceStatus status;

    /** Null when SKIPPED without ever materializing, or after a THIS_ONE delete removes the PENDING row. */
    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
