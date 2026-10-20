package br.com.mkfinance.api.fixedincome;

import br.com.mkfinance.application.fixedincome.FixedIncomeComparison;
import br.com.mkfinance.application.fixedincome.FixedIncomeSimulation;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.InstrumentType;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Contract schema {@code FixedIncomeSimulationResponse}. Money in cents; percentages rounded to four
 * decimals.
 *
 * @param results one per simulation requested, in the same order
 * @param comparison present only when two simulations were requested
 */
public record FixedIncomeSimulationResponse(List<Result> results, Comparison comparison) {

    /**
     * @param assumptions what the projection ran with, so the client can show what was filled in
     * @param netAnnualEquivalentPercent the rate a.a. that makes each contribution reach the net amount
     * @param series one point per month, each as if redeemed that day
     */
    public record Result(
            InstrumentType type,
            Indexer indexer,
            BigDecimal rate,
            LocalDate startDate,
            LocalDate endDate,
            int months,
            Assumptions assumptions,
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
    }

    /** Only the ones the indexer uses are present. */
    public record Assumptions(BigDecimal cdiAnnualPercent, BigDecimal selicAnnualPercent, BigDecimal ipcaAnnualPercent) {
    }

    public record Point(int month, LocalDate date, long investedCents, long grossCents, long netCents) {
    }

    /** @param betterIndex position (0 or 1) of the simulation that yields more net; absent on a tie */
    public record Comparison(Integer betterIndex, long netDifferenceCents, double netAnnualEquivalentDifferencePercent) {
    }

    public static FixedIncomeSimulationResponse of(List<FixedIncomeSimulation> simulations, FixedIncomeComparison comparison) {
        return new FixedIncomeSimulationResponse(
                simulations.stream().map(FixedIncomeSimulationResponse::result).toList(),
                comparison == null ? null : new Comparison(
                        comparison.betterIndex(),
                        comparison.netDifferenceCents(),
                        round(comparison.netAnnualEquivalentDifferencePercent())));
    }

    private static Result result(FixedIncomeSimulation simulation) {
        FixedIncomeParams p = simulation.params();
        FixedIncomeResult r = simulation.result();
        return new Result(
                p.type(), p.indexer(), p.rate(), p.startDate(), p.endDate(), p.months(),
                new Assumptions(p.cdiAnnualPercent(), p.selicAnnualPercent(), p.ipcaAnnualPercent()),
                r.investedCents(), r.grossCents(), r.grossYieldCents(), r.iofCents(), r.incomeTaxCents(),
                r.custodyCents(), r.netCents(), r.netYieldCents(),
                round(r.netReturnPercent()), round(r.netAnnualEquivalentPercent()),
                r.series().stream()
                        .map(pt -> new Point(pt.month(), pt.date(), pt.investedCents(), pt.grossCents(), pt.netCents()))
                        .toList());
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
