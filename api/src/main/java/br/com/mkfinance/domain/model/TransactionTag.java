package br.com.mkfinance.domain.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps {@code transaction_tag} (V17__transaction_tag.sql, BE-8) — the many-to-many between
 * {@link Transaction} and {@link Tag}. No {@code @ManyToMany}: both sides are plain UUID columns
 * (convention shared with every other join in this codebase), so nothing lazy-loads and every read
 * joins explicitly and scopes through {@code workspace_id} on one side or the other.
 */
@Entity
@Table(name = "transaction_tag")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionTag {

    @EmbeddedId
    private TransactionTagId id;
}
