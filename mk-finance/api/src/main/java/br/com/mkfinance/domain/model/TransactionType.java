package br.com.mkfinance.domain.model;

/**
 * PLANEJAMENTO §4 — tipo de lançamento.
 *
 * <p>{@link #TRANSFER} legs are never income nor expense in any aggregation (R4): reports must
 * filter them out explicitly. A contribution ("aporte", R9) is a TRANSFER whose destination is an
 * INVESTMENT/SAVINGS account — it is still not an expense, it only consumes a pillar's allocation.
 */
public enum TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER
}
