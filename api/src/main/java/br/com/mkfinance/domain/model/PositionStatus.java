package br.com.mkfinance.domain.model;

/**
 * Where a fixed-income position is in its life. "Waiting for payment" is not a status: it is an
 * {@link #ACTIVE} position whose maturity date has come, so it is derived from the date and never stored.
 */
public enum PositionStatus {
    ACTIVE,
    /** The title was paid and the real yield was booked. */
    SETTLED
}
