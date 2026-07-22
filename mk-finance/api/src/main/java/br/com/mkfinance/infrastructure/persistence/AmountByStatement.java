package br.com.mkfinance.infrastructure.persistence;

import java.util.UUID;

/**
 * One {@code (statement, summed cents)} row — the shape both {@code InstallmentRepository} and
 * {@code StatementPaymentRepository} aggregate into, so a screen showing many statements resolves
 * their totals in two queries instead of two per statement.
 */
public record AmountByStatement(UUID statementId, long amountCents) {
}
