package br.com.mkfinance.domain.model;

import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
 * Maps {@code fixed_income_position} (V26__fixed_income_position.sql): one fixed-income title,
 * detailing the balance of an investment account — it neither moves money nor adds to the balance.
 *
 * <p>Account, principal, purchase date and vault live on the position whatever its origin: copied
 * from the purchase transfer when {@link #contributionTransactionId} is set, informed by the user
 * for a title bought before the app. Like every other entity here, no JPA relationship connects it
 * to {@link Transaction}, {@link Account} or {@link Vault}; they are plain UUID columns.
 */
@Entity
@Table(name = "fixed_income_position")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FixedIncomePosition {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    /** Where the title's yield goes when it is paid; null means the account's unallocated slice. */
    @Column(name = "vault_id")
    private UUID vaultId;

    /** The IN leg of the purchase transfer; null for a title bought before the app. */
    @Column(name = "contribution_transaction_id")
    private UUID contributionTransactionId;

    @Column(name = "principal_cents", nullable = false)
    private long principalCents;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private InstrumentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Indexer indexer;

    /** % a.a. for a fixed rate; % of the CDI or Selic for the other indexers. */
    @Column(nullable = false, precision = 9, scale = 4)
    private BigDecimal rate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    @Builder.Default
    private PositionStatus status = PositionStatus.ACTIVE;

    @Column(name = "settled_date")
    private LocalDate settledDate;

    @Column(name = "received_cents")
    private Long receivedCents;

    /** What the app estimated the title would net on the day it was settled. */
    @Column(name = "estimated_net_cents")
    private Long estimatedNetCents;

    @Column(name = "yield_cents")
    private Long yieldCents;

    @Column(name = "yield_transaction_id")
    private UUID yieldTransactionId;

    @Column(name = "return_transfer_transaction_id")
    private UUID returnTransferTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public boolean isSettled() {
        return status == PositionStatus.SETTLED;
    }

    public boolean isManual() {
        return contributionTransactionId == null;
    }
}
