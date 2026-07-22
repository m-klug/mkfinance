package br.com.mkfinance.application.card;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Validated payload for paying a statement.
 *
 * @param accountId the account debited; a partial amount simply leaves the rest outstanding (§9.5)
 */
public record StatementPaymentInput(UUID accountId, long amountCents, LocalDate paymentDate) {
}
