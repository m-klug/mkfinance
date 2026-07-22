package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Maps {@code statement_payment} (V1__schema.sql) — money actually leaving an account to settle a
 * statement (R2). A statement may collect several of these: paying part of it now and the rest later
 * simply adds rows, and the status follows the running sum.
 *
 * <p>{@link #transactionId} points at the EXPENSE row that debits the account, created through
 * {@code application/transaction/TransactionService#createSettledExpense} rather than inserted here.
 * That indirection is what keeps the payment visible in the extrato and inside the account balance
 * with no duplicated logic — the credit-card feature owns the bill, BE-3 owns the money movement.
 */
@Entity
@Table(name = "statement_payment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementPayment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "statement_id", nullable = false)
    private UUID statementId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    /** The EXPENSE transaction debited on the account; system-categorized, so reports skip it (R2). */
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
