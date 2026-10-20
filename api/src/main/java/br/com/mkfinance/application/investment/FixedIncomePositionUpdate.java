package br.com.mkfinance.application.investment;

import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * The editable facts of an active position; replaces them all, so {@code vaultId} null means "no
 * vault". {@code principalCents} and {@code purchaseDate} only apply to a manual position (a linked
 * one takes them from its purchase transfer) and are ignored for it; left null on a manual one they
 * stay as they are.
 */
public record FixedIncomePositionUpdate(
        InstrumentType type,
        Indexer indexer,
        BigDecimal rate,
        LocalDate maturityDate,
        String name,
        UUID vaultId,
        Long principalCents,
        LocalDate purchaseDate) {
}
