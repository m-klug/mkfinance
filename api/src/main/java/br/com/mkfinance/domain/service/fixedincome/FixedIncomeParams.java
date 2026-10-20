package br.com.mkfinance.domain.service.fixedincome;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One fixed-income simulation.
 *
 * <p>The initial amount is applied on {@code startDate}; the monthly contribution is applied on
 * the same day of each following month, up to the month before the end (a contribution made on the
 * redemption day would earn nothing). The redemption is {@code months} months after the start.
 *
 * @param rate meaning depends on the indexer: % a.a. for {@code PREFIXED} and {@code IPCA_PLUS},
 *     % of the CDI/Selic for the other two (110 means 110% of the CDI)
 * @param monthlyContributionCents zero for none
 * @param cdiAnnualPercent CDI in % a.a., held constant over the whole term; required by {@code CDI_PERCENT}
 * @param selicAnnualPercent Selic in % a.a., held constant; required by {@code SELIC_PERCENT}
 * @param ipcaAnnualPercent projected IPCA in % a.a., held constant; required by {@code IPCA_PLUS}
 */
public record FixedIncomeParams(
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

    public static final int MAX_MONTHS = 600;

    public enum InstrumentType {
        CDB(true, false),
        LC(true, false),
        /** Exempt from income tax and IOF for an individual. */
        LCI(false, false),
        /** Exempt from income tax and IOF for an individual. */
        LCA(false, false),
        TESOURO_SELIC(true, true),
        TESOURO_PREFIXADO(true, true),
        TESOURO_IPCA(true, true);

        private final boolean taxed;
        private final boolean custody;

        InstrumentType(boolean taxed, boolean custody) {
            this.taxed = taxed;
            this.custody = custody;
        }

        /** Income tax and IOF apply to the yield. */
        public boolean taxed() {
            return taxed;
        }

        /** B3 charges custody on the position. */
        public boolean hasCustody() {
            return custody;
        }
    }

    public enum Indexer {
        PREFIXED,
        CDI_PERCENT,
        SELIC_PERCENT,
        IPCA_PLUS
    }

    public FixedIncomeParams {
        require(type != null, "type is required");
        require(indexer != null, "indexer is required");
        require(startDate != null, "startDate is required");
        require(rate != null && rate.signum() >= 0, "rate must be zero or positive");
        require(initialCents >= 0, "initialCents must not be negative");
        require(monthlyContributionCents >= 0, "monthlyContributionCents must not be negative");
        require(initialCents + monthlyContributionCents > 0, "something must be invested");
        require(months >= 1 && months <= MAX_MONTHS, "months must be between 1 and " + MAX_MONTHS);
        require(initialCents > 0 || months >= 2, "a single month with no initial amount applies nothing");
        require(indexerFits(type, indexer), indexer + " is not available for " + type);
        require(indexer != Indexer.CDI_PERCENT || cdiAnnualPercent != null, "cdiAnnualPercent is required");
        require(indexer != Indexer.SELIC_PERCENT || selicAnnualPercent != null, "selicAnnualPercent is required");
        require(indexer != Indexer.IPCA_PLUS || ipcaAnnualPercent != null, "ipcaAnnualPercent is required");
    }

    public LocalDate endDate() {
        return startDate.plusMonths(months);
    }

    /** The indexers each instrument can be sold with. */
    public static boolean indexerFits(InstrumentType type, Indexer indexer) {
        return switch (type) {
            case CDB, LC, LCI, LCA -> indexer == Indexer.PREFIXED
                    || indexer == Indexer.CDI_PERCENT
                    || indexer == Indexer.IPCA_PLUS;
            case TESOURO_SELIC -> indexer == Indexer.SELIC_PERCENT;
            case TESOURO_PREFIXADO -> indexer == Indexer.PREFIXED;
            case TESOURO_IPCA -> indexer == Indexer.IPCA_PLUS;
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
