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
 * Maps {@code reimbursement} (V23__reimbursement.sql, ADR-0002): how much of an INCOME paid back
 * how much of an EXPENSE. Plain UUID columns like every other join here. The effective amount of an
 * expense is never stored — it is always {@code amount − sum(reimbursement.amountCents)}.
 */
@Entity
@Table(name = "reimbursement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reimbursement {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "income_transaction_id", nullable = false)
    private UUID incomeTransactionId;

    @Column(name = "expense_transaction_id", nullable = false)
    private UUID expenseTransactionId;

    /** Always positive. */
    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
