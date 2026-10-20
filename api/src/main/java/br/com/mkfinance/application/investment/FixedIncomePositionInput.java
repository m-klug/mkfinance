package br.com.mkfinance.application.investment;

import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A position to create. Exactly one origin: either {@code contributionTransactionId} (the purchase
 * transfer, which supplies account, amount, date and vault), or {@code accountId} +
 * {@code principalCents} + {@code purchaseDate} (a title bought before the app).
 *
 * @param vaultId where the yield goes on payment; for a linked position, left null it is the vault of
 *     the purchase transfer
 */
public record FixedIncomePositionInput(
        UUID contributionTransactionId,
        UUID accountId,
        Long principalCents,
        LocalDate purchaseDate,
        UUID vaultId,
        String name,
        InstrumentType type,
        Indexer indexer,
        BigDecimal rate,
        LocalDate maturityDate) {
}
