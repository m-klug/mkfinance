package br.com.mkfinance.application.card;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Validated payload for paying a statement.
 *
 * @param accountId the account debited; a partial amount simply leaves the rest outstanding (§9.5)
 * @param externalId the bank's id for the debit (OFX FITID) when the payment comes from a statement
 *     import, so importing the same file again does not pay twice; null otherwise
 */
public record StatementPaymentInput(UUID accountId, long amountCents, LocalDate paymentDate, String externalId) {

    public StatementPaymentInput(UUID accountId, long amountCents, LocalDate paymentDate) {
        this(accountId, amountCents, paymentDate, null);
    }
}
