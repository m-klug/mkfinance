package br.com.mkfinance.domain.service.fixedincome;

import java.time.LocalDate;
import java.util.List;

/**
 * What a simulation yields if everything is redeemed on the end date.
 *
 * @param netAnnualEquivalentPercent the single rate a.a. that, applied to every contribution for
 *     its own time, would give {@code netCents}; the number to compare two investments by
 * @param series one point per month, from the start to the end, each as if redeemed that day
 */
public record FixedIncomeResult(
        long investedCents,
        long grossCents,
        long grossYieldCents,
        long iofCents,
        long incomeTaxCents,
        long custodyCents,
        long netCents,
        long netYieldCents,
        double netReturnPercent,
        double netAnnualEquivalentPercent,
        List<Point> series) {

    public record Point(int month, LocalDate date, long investedCents, long grossCents, long netCents) {
    }
}
