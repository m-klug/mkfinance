package br.com.mkfinance.application.investment;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The payment of a title, as the user saw it in the bank.
 *
 * @param settledDate the day the credit fell; not before the purchase and not in the future
 * @param receivedCents the net amount received: principal plus the yield, already net of income tax
 *     and IOF; never below the principal
 * @param returnTransferTransactionId the transfer back to a checking account that carried this money,
 *     when it is already registered; optional, can be linked later
 */
public record PositionSettlementInput(LocalDate settledDate, long receivedCents, UUID returnTransferTransactionId) {
}
