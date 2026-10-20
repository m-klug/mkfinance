package br.com.mkfinance.domain.service.fixedincome;

import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.NavigableMap;

/**
 * What a fixed-income title that was really bought is worth on a given day, gross and net of IOF,
 * income tax and custody. The same arithmetic as the simulator, for one application, with the growth
 * of the days already lived taken from the real daily index ({@link HistoricalThenConstantGrowth}).
 *
 * <p>Only titles that follow a fixed rate, a percentage of the CDI or a percentage of the Selic are
 * valued; an IPCA-linked title would need the real monthly IPCA and the Tesouro's update rule, which
 * this does not model. Pure and static: no Spring, no clock — today comes in as an argument.
 */
public final class FixedIncomePositionValuator {

    private FixedIncomePositionValuator() {
    }

    /**
     * @param rate % a.a. for PREFIXED; % of the CDI/Selic for the other two (110 means 110%)
     */
    public record PositionTerms(
            InstrumentType type,
            Indexer indexer,
            BigDecimal rate,
            long principalCents,
            LocalDate purchaseDate,
            LocalDate maturityDate) {

        public PositionTerms {
            require(type != null && indexer != null, "type and indexer are required");
            require(indexer != Indexer.IPCA_PLUS, "a title linked to the IPCA is not valued yet");
            require(FixedIncomeParams.indexerFits(type, indexer), indexer + " is not available for " + type);
            require(rate != null && rate.signum() >= 0, "rate must be zero or positive");
            require(principalCents > 0, "principalCents must be positive");
            require(purchaseDate != null && maturityDate != null, "purchaseDate and maturityDate are required");
            require(maturityDate.isAfter(purchaseDate), "maturityDate must be after purchaseDate");
        }
    }

    /**
     * @param asOf the day the title is valued for (never past the maturity)
     * @param lastIndexDate the latest daily index actually used for the days already lived, or null
     *     when none was (a fixed rate, or a title bought today); lets the caller say "up to the
     *     index of this date" when the provider is behind
     */
    public record Valuation(
            LocalDate asOf,
            long principalCents,
            long grossCents,
            long grossYieldCents,
            long iofCents,
            long incomeTaxCents,
            long custodyCents,
            long netCents,
            long netYieldCents,
            LocalDate lastIndexDate) {
    }

    /** Today's value (if redeemed now) and the value at the maturity. */
    public record Estimate(Valuation today, Valuation atMaturity) {
    }

    /**
     * @param today the current business date; days before it use {@code dailyPercents}, from it on
     *     {@code futureAnnualPercent}
     * @param dailyPercents the stored index, % per day, covering at least the purchase to today
     * @param futureAnnualPercent the current CDI or Selic in % a.a., held constant for the days to
     *     come; may be null for a fixed-rate title or when every day was already lived with history
     */
    public static Valuation valueOn(
            PositionTerms terms,
            LocalDate asOf,
            LocalDate today,
            NavigableMap<LocalDate, BigDecimal> dailyPercents,
            BigDecimal futureAnnualPercent) {
        if (asOf.isBefore(terms.purchaseDate())) {
            throw new IllegalArgumentException("asOf is before the purchase: " + asOf + " < " + terms.purchaseDate());
        }
        LocalDate date = asOf.isAfter(terms.maturityDate()) ? terms.maturityDate() : asOf;

        GrowthModel growth = new HistoricalThenConstantGrowth(
                terms.indexer(), terms.rate(), today, dailyPercents, futureAnnualPercent);
        long age = ChronoUnit.DAYS.between(terms.purchaseDate(), date);
        FixedIncomeSettlement.Result settled = FixedIncomeSettlement.settle(
                terms.type(), terms.principalCents(), growth.factor(terms.purchaseDate(), date), age);

        long netCents = settled.netCents();
        return new Valuation(
                date,
                terms.principalCents(),
                settled.grossCents(),
                settled.grossCents() - terms.principalCents(),
                settled.iofCents(),
                settled.incomeTaxCents(),
                settled.custodyCents(),
                netCents,
                netCents - terms.principalCents(),
                lastIndexDateUsed(terms, date, today, dailyPercents));
    }

    /** The value today and at the maturity; today is capped at the maturity for a title already due. */
    public static Estimate estimate(
            PositionTerms terms,
            LocalDate today,
            NavigableMap<LocalDate, BigDecimal> dailyPercents,
            BigDecimal futureAnnualPercent) {
        return new Estimate(
                valueOn(terms, today, today, dailyPercents, futureAnnualPercent),
                valueOn(terms, terms.maturityDate(), today, dailyPercents, futureAnnualPercent));
    }

    private static LocalDate lastIndexDateUsed(
            PositionTerms terms, LocalDate date, LocalDate today, NavigableMap<LocalDate, BigDecimal> dailyPercents) {
        if (terms.indexer() == Indexer.PREFIXED) {
            return null;
        }
        LocalDate lastLivedDay = (date.isBefore(today) ? date : today).minusDays(1);
        if (lastLivedDay.isBefore(terms.purchaseDate())) {
            return null;
        }
        LocalDate latest = dailyPercents.floorKey(lastLivedDay);
        return latest != null && !latest.isBefore(terms.purchaseDate()) ? latest : null;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
