package br.com.mkfinance.domain.model;

/**
 * Which side of a mirrored transfer pair (R4) a {@link Transaction} leg is.
 *
 * <p>Both legs of a transfer store a positive {@code amount_cents} (DB check constraint), so the
 * amount alone cannot tell money leaving one account from money arriving in another. This column
 * (added in {@code V7__transfer_direction.sql}) is what makes the sign deterministic for
 * {@code BalanceCalculator} and for allocation reports.
 *
 * <p>Only the {@link #IN} leg may carry a {@code contribution_pillar} (DB check constraint too),
 * so aggregating contributions per pillar can never double-count a single transfer.
 */
public enum TransferDirection {
    /** Money leaving this leg's account — subtracts from its balance. */
    OUT,
    /** Money arriving in this leg's account — adds to its balance. */
    IN
}
