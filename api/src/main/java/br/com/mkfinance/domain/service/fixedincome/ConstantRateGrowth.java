package br.com.mkfinance.domain.service.fixedincome;

import java.time.LocalDate;

/**
 * Growth with every rate held constant over the whole term, as the simulator projects: the CDI, the
 * Selic and the IPCA are the single values the caller supplied. Compounds over business days.
 */
public record ConstantRateGrowth(FixedIncomeParams params) implements GrowthModel {

    @Override
    public double factor(LocalDate from, LocalDate to) {
        int businessDays = BrazilianBusinessDays.countBetween(from, to);
        double rate = params.rate().doubleValue() / 100;
        return switch (params.indexer()) {
            case PREFIXED -> Math.pow(1 + rate, businessDays / (double) BUSINESS_DAYS_PER_YEAR);
            case IPCA_PLUS -> {
                double ipca = params.ipcaAnnualPercent().doubleValue() / 100;
                yield Math.pow((1 + ipca) * (1 + rate), businessDays / (double) BUSINESS_DAYS_PER_YEAR);
            }
            case CDI_PERCENT -> percentOfIndex(params.cdiAnnualPercent().doubleValue() / 100, rate, businessDays);
            case SELIC_PERCENT -> percentOfIndex(params.selicAnnualPercent().doubleValue() / 100, rate, businessDays);
        };
    }

    /** A post-fixed yield: the daily rate of the annual index, scaled by the percentage, compounded daily. */
    static double percentOfIndex(double annualIndex, double percent, int businessDays) {
        return Math.pow(1 + dailyRate(annualIndex) * percent, businessDays);
    }

    /** The daily rate equivalent to an annual one over 252 business days. */
    static double dailyRate(double annualIndex) {
        return Math.pow(1 + annualIndex, 1.0 / BUSINESS_DAYS_PER_YEAR) - 1;
    }
}
