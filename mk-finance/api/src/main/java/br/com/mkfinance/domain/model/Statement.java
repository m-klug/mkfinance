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
import java.time.YearMonth;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Maps {@code statement} (V1__schema.sql) — one credit-card bill, unique per
 * {@code (card, reference_month)} and created lazily the first time something needs it (R3).
 *
 * <p>The statement carries no {@code workspace_id} of its own: it is reachable only through its
 * card, so {@code StatementRepository} joins {@code card} to enforce isolation (R8) rather than
 * duplicating the column and risking the two disagreeing.
 *
 * <p>It also stores no total. The total <em>is</em> the sum of the installments allocated to it, so
 * keeping a copy would mean maintaining it on every purchase edit and eventually drifting; it is
 * summed on demand instead. The persisted {@link #status} therefore only ever tracks payment —
 * see {@link StatementStatus} for why CLOSED is derived instead.
 */
@Entity
@Table(name = "statement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Statement {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    /** First day of the reference month — the month in which this statement closes. */
    @Column(name = "reference_month", nullable = false)
    private LocalDate referenceMonth;

    @Column(name = "closing_date", nullable = false)
    private LocalDate closingDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    private StatementStatus status = StatementStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public YearMonth referenceYearMonth() {
        return YearMonth.from(referenceMonth);
    }

    /**
     * A fully paid statement is frozen (R6): its installments cannot be edited, moved or deleted,
     * and corrections go through an adjusting entry instead. Partially paid ones stay editable —
     * the MVP keeps the outstanding remainder as a plain balance, with no interest (§9.5).
     */
    public boolean isImmutable() {
        return status == StatementStatus.PAID;
    }

    /**
     * The status the client sees: the stored payment status, except that an untouched statement
     * past its closing date reads as CLOSED.
     *
     * @param today current business date in America/Sao_Paulo (R7)
     */
    public StatementStatus effectiveStatus(LocalDate today) {
        if (status == StatementStatus.OPEN && today.isAfter(closingDate)) {
            return StatementStatus.CLOSED;
        }
        return status;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
