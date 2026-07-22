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
     * R6 — a fully paid statement is frozen <em>against new or changed charges</em>: its installments
     * cannot be edited, moved or deleted, and corrections there go through an adjusting entry
     * instead. Partially paid ones stay chargeable — the MVP keeps the outstanding remainder as a
     * plain balance, with no interest (§9.5).
     *
     * <p><strong>This is deliberately narrower than "the statement is read-only".</strong> Reversing
     * a payment is exempt, and must stay exempt: the payment is precisely what made the statement
     * PAID, so treating PAID as a blanket freeze would make the most likely mistake — paying the
     * wrong bill, the wrong account or the wrong amount — the one mistake impossible to undo. A
     * reversal only removes a {@code statement_payment} row, and the status follows the remaining
     * sum straight back to PARTIAL or OPEN.
     *
     * <p>Read it as a question about charges, and ask it only where a charge is at stake — today
     * that is {@code CardPurchaseService} alone. Paying refuses a settled bill on its own terms
     * (nothing is owed, derived from {@link br.com.mkfinance.application.card.StatementTotals}),
     * not by consulting this flag.
     */
    public boolean chargesFrozen() {
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
