package br.com.mkfinance.domain.service.fixedincome;

import java.time.LocalDate;

/**
 * How much one application grows between two dates. The simulator projects with constant rates
 * ({@link ConstantRateGrowth}); a position that already exists uses the real daily index for the days
 * that have passed and a constant for the days that have not
 * ({@link HistoricalThenConstantGrowth}). Everything else — taxes, custody, the series — is the
 * same for both, which is why it hangs off this one seam.
 */
public interface GrowthModel {

    /** CDI, Selic and fixed rates compound over the 252 business days of a year. */
    int BUSINESS_DAYS_PER_YEAR = 252;

    /** Growth factor (1.10 means +10%) of one application held from {@code from} to {@code to}. */
    double factor(LocalDate from, LocalDate to);
}
