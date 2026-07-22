package br.com.mkfinance.domain.model;

/**
 * PLANEJAMENTO §5 R5 — an account's current balance counts {@link #SETTLED} only; the projected
 * balance also counts {@link #PENDING} (scheduled bills / future income).
 */
public enum TransactionStatus {
    SETTLED,
    PENDING
}
