package br.com.mkfinance.domain.model;

/**
 * A vault goal's status, computed by {@code domain/service/VaultGoalCalculator}
 * (DESENVOLVIMENTO-CAIXINHAS.md §4.6). REACHED wins over OVERDUE — a goal hit before its deadline
 * stops asking, even after the date passes.
 */
public enum VaultGoalStatus {
    IN_PROGRESS,
    REACHED,
    OVERDUE
}
