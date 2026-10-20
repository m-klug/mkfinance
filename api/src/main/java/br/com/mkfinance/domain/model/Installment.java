package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Maps {@code installment} (V1__schema.sql) — one slice of a {@link CardPurchase} allocated to one
 * {@link Statement}. This is the join between what was bought and what is billed, and the only
 * place a statement's total comes from.
 *
 * <p>All installments are created up-front when the purchase is registered, not month by month, so
 * a 12× purchase immediately shows its full commitment across the next twelve statements — which is
 * also what makes the available limit honest (it already accounts for installments not yet billed).
 *
 * <p>{@link #amountCents} comes from {@code InstallmentSplitter}: equal parts with the cent
 * remainder on the last {@link #number} (R1).
 */
@Entity
@Table(name = "installment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Installment {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "purchase_id", nullable = false)
    private UUID purchaseId;

    /** 1-based position within the purchase; the last one carries the split's remainder. */
    @Column(nullable = false)
    private short number;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(name = "statement_id", nullable = false)
    private UUID statementId;

    /**
     * Fingerprint of the statement-PDF line this installment was imported from or linked to; null for
     * installments typed by hand and never reconciled. See V22__installment_external_id.sql.
     */
    @Column(name = "external_id", length = 64)
    private String externalId;
}
