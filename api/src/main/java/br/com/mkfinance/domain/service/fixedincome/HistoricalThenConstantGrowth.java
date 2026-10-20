package br.com.mkfinance.domain.service.fixedincome;

import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.NavigableMap;

/**
 * Growth of a title that already exists: the days that have passed use what the index really was,
 * the days to come use one constant. Only post-fixed titles care; a fixed rate is the same either way.
 *
 * <p>For a post-fixed title each business day {@code d} multiplies the amount by
 * {@code 1 + dailyIndex(d) × percentage}:
 * <ul>
 *   <li>{@code d < today}: the stored daily index of that day. A business day with nothing stored
 *       (not published yet, or a gap) carries the latest stored value forward, so a lagging provider
 *       never leaves recent days out of the count; with nothing stored at all the constant is used.
 *   <li>{@code d >= today}: the constant, from the current annual rate. Today's own rate is only
 *       published after the day, so today counts as future.
 * </ul>
 *
 * <p>The days are counted by the national business-day calendar, which matches the one the index is
 * published on.
 */
public record HistoricalThenConstantGrowth(
        Indexer indexer,
        BigDecimal rate,
        LocalDate today,
        NavigableMap<LocalDate, BigDecimal> dailyPercents,
        BigDecimal futureAnnualPercent)
        implements GrowthModel {

    @Override
    public double factor(LocalDate from, LocalDate to) {
        double percentage = rate.doubleValue() / 100;
        if (indexer == Indexer.PREFIXED) {
            int businessDays = BrazilianBusinessDays.countBetween(from, to);
            return Math.pow(1 + percentage, businessDays / (double) BUSINESS_DAYS_PER_YEAR);
        }

        double factor = 1;
        for (LocalDate day = from; day.isBefore(to); day = day.plusDays(1)) {
            if (!BrazilianBusinessDays.isBusinessDay(day)) {
                continue;
            }
            factor *= 1 + dailyRateOf(day) * percentage;
        }
        return factor;
    }

    /** The index's rate of one business day, as a fraction (0.0005 is 0.05% a day). */
    private double dailyRateOf(LocalDate day) {
        if (day.isBefore(today)) {
            Map.Entry<LocalDate, BigDecimal> known = dailyPercents.floorEntry(day);
            if (known != null) {
                return known.getValue().doubleValue() / 100;
            }
        }
        return constantDailyRate();
    }

    private double constantDailyRate() {
        if (futureAnnualPercent == null) {
            throw new IllegalArgumentException("the current annual rate is needed for the days without history");
        }
        return ConstantRateGrowth.dailyRate(futureAnnualPercent.doubleValue() / 100);
    }
}
