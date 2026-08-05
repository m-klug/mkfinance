package br.com.mkfinance.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Composite key of {@code transaction_tag}: (transaction_id, tag_id). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TransactionTagId implements Serializable {

    @Column(name = "transaction_id")
    private UUID transactionId;

    @Column(name = "tag_id")
    private UUID tagId;
}
