package br.com.mkfinance.application.fixedincome;

import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What the user asks to simulate. Every assumption left null is filled from the stored market
 * rates; {@code startDate} left null is today.
 */
public record FixedIncomeInput(
        InstrumentType type,
        Indexer indexer,
        BigDecimal rate,
        long initialCents,
        long monthlyContributionCents,
        int months,
        LocalDate startDate,
        BigDecimal cdiAnnualPercent,
        BigDecimal selicAnnualPercent,
        BigDecimal ipcaAnnualPercent) {
}
