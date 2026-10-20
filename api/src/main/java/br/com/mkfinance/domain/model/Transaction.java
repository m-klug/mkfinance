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
 * Maps {@code transaction} (V1__schema.sql + V7__transfer_direction.sql). Like {@link Account},
 * foreign keys are plain scalar columns rather than JPA relationships so every query filters by
 * {@code workspaceId} explicitly (PLANEJAMENTO §5 R8) and nothing is lazily loaded by accident.
 *
 * <p>Money is a {@code long} of cents and never anything else (R1). {@link #date} is the business
 * date in {@code America/Sao_Paulo} as a {@link LocalDate} (R7) — {@code createdAt}/{@code
 * updatedAt} are the only instants here, and they are audit-only.
 *
 * <p>A transfer (R4) is a <em>pair</em> of rows: one {@link TransferDirection#OUT} leg on the
 * source account and one {@link TransferDirection#IN} leg on the destination, sharing amount,
 * date and description, each pointing at the other through {@link #transferPairId}. Both legs are
 * always created, edited, settled and deleted together — see
 * {@code application/transaction/TransferService}. Transfer legs carry no category (DB check
 * constraint {@code ck_transaction_category}).
 */
@Entity
@Table(name = "transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Required for INCOME/EXPENSE, always null for TRANSFER legs. */
    @Column(name = "category_id")
    private UUID categoryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private TransactionType type;

    /** Always positive; the sign comes from {@link #type} + {@link #transferDirection}. */
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "note")
    private String note;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private TransactionStatus status = TransactionStatus.SETTLED;

    /** The other leg of the mirrored transfer pair; null for INCOME/EXPENSE. */
    @Column(name = "transfer_pair_id")
    private UUID transferPairId;

    /** Non-null only on TRANSFER legs; see {@link TransferDirection}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_direction", length = 3)
    private TransferDirection transferDirection;

    /**
     * The pillar a contribution (R9) consumes. Only ever set on the {@link TransferDirection#IN}
     * leg of a transfer into an INVESTMENT/SAVINGS account, so summing this column across
     * transactions counts each contribution exactly once.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "contribution_pillar", length = 20)
    private ContributionPillar contributionPillar;

    /**
     * How much of this EXPENSE the user expects to get back from other people (V23, ADR-0002); null
     * when none. The ceiling for the sum of its {@code reimbursement} links. Always null on
     * INCOME/TRANSFER rows.
     */
    @Column(name = "reimbursable_cents")
    private Long reimbursableCents;

    /**
     * The {@link RecurrenceRule} that materialized this row, when it came from a recurrence (CT-1,
     * V14__recurrences.sql). Null for every ordinary entry. The extrato and the calendar both use it
     * to badge the row and offer editing the whole series.
     */
    @Column(name = "recurrence_id")
    private UUID recurrenceId;

    /**
     * The bank's id for the movement this row came from (OFX {@code FITID}), set only by a statement
     * import. Unique per account, which is what makes re-importing the same file a no-op.
     */
    @Column(name = "external_id", length = 64)
    private String externalId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public boolean isTransfer() {
        return type == TransactionType.TRANSFER;
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
