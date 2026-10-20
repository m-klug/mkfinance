package br.com.mkfinance.api.investment;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Contract schema {@code PositionSettleRequest}: the payment of a title as the bank showed it.
 *
 * @param settledDate the day the credit fell; not before the purchase, not in the future
 * @param receivedCents the net amount received (principal plus yield, already net of income tax and
 *     IOF); not below the principal
 * @param returnTransferTransactionId the outgoing leg of the transfer back to checking, when already
 *     registered; optional
 */
public record PositionSettleRequest(
        @NotNull LocalDate settledDate,
        @NotNull @Positive Long receivedCents,
        UUID returnTransferTransactionId) {
}
