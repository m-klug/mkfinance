package br.com.mkfinance.application.vault;

import java.time.LocalDate;

/**
 * The application-layer shape of {@code VaultYieldRequest} (CT-2) — {@code api/vault/VaultYieldMapper}
 * translates between the two so {@link VaultYieldService} never depends on the web DTO.
 *
 * @param description nullable — {@link VaultYieldService} falls back to the category's own name
 *     when omitted, since {@code transaction.description} is {@code NOT NULL}
 */
public record VaultYieldInput(long amountCents, LocalDate date, String description) {
}
