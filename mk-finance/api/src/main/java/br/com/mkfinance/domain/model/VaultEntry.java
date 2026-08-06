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
 * Maps {@code vault_entry} (V19__vault_entry.sql) — the single ledger every vault's balance is
 * derived from (DESENVOLVIMENTO-CAIXINHAS.md §4.1). Immutable once written: nothing here is ever
 * updated in place, only inserted or deleted (through its owning {@link Transaction}'s cascade, or
 * directly for {@link VaultEntrySource#REALLOCATION}), so there is no {@code updated_at} column.
 *
 * <p>{@link #amountCents} is {@code null} on CONTRIBUTION/WITHDRAWAL — it means "mirror
 * {@code br.com.mkfinance.domain.service.BalanceCalculator#signedAmountCents} of the transaction
 * behind {@link #transactionId}", so the transaction's amount/date/status can change freely without
 * ever touching this row. Only YIELD and REALLOCATION carry their own signed value, since neither
 * mirrors a single transaction 1:1.
 */
@Entity
@Table(name = "vault_entry")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VaultEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "vault_id", nullable = false)
    private UUID vaultId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private VaultEntrySource source;

    /** Non-null for CONTRIBUTION/WITHDRAWAL/YIELD; always null for REALLOCATION. */
    @Column(name = "transaction_id")
    private UUID transactionId;

    /** Signed cents; {@code null} on CONTRIBUTION/WITHDRAWAL means "mirror the transaction". */
    @Column(name = "amount_cents")
    private Long amountCents;

    /** Groups the two sides of a reallocation, or the single side when the other is unallocated. */
    @Column(name = "reallocation_id")
    private UUID reallocationId;

    /** Only set for REALLOCATION — the other sources take their date from the transaction. */
    @Column(name = "date")
    private LocalDate date;

    /** Only set for REALLOCATION. */
    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
