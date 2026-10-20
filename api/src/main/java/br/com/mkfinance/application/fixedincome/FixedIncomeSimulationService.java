package br.com.mkfinance.application.fixedincome;

import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.application.marketrate.MarketRateService;
import br.com.mkfinance.domain.exception.AppException;
import br.com.mkfinance.domain.exception.ErrorCode;
import br.com.mkfinance.domain.model.MarketRate;
import br.com.mkfinance.domain.model.MarketRateCode;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeParams.Indexer;
import br.com.mkfinance.domain.service.fixedincome.FixedIncomeSimulator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Runs fixed-income simulations. Persists nothing and reads nothing of the user's: the only data it
 * touches are the public market rates, and only when an assumption was left for it to fill — a
 * request that brings every assumption never asks the provider.
 */
@Service
@RequiredArgsConstructor
public class FixedIncomeSimulationService {

    /** Two equivalent rates closer than this (percentage points) are a tie. */
    private static final double TIE = 0.005;

    private final MarketRateService marketRates;
    private final BusinessClock clock;

    public List<FixedIncomeSimulation> simulate(List<FixedIncomeInput> inputs) {
        Map<MarketRateCode, BigDecimal> stored = null;
        List<FixedIncomeSimulation> simulations = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            FixedIncomeInput input = inputs.get(i);
            if (!FixedIncomeParams.indexerFits(input.type(), input.indexer())) {
                throw AppException.of(ErrorCode.SIMULATION_INDEXER_NOT_AVAILABLE, HttpStatus.BAD_REQUEST,
                        Map.of("index", i, "type", input.type().name(), "indexer", input.indexer().name()));
            }
            if (input.initialCents() <= 0 && (input.monthlyContributionCents() <= 0 || input.months() < 2)) {
                throw AppException.of(ErrorCode.SIMULATION_NOTHING_APPLIED, HttpStatus.BAD_REQUEST, Map.of("index", i));
            }
            if (stored == null && needsStoredRate(input)) {
                stored = storedRates();
            }
            simulations.add(run(input, stored));
        }
        return simulations;
    }

    /** Compares exactly two simulations. */
    public FixedIncomeComparison compare(FixedIncomeSimulation first, FixedIncomeSimulation second) {
        double gap = first.result().netAnnualEquivalentPercent() - second.result().netAnnualEquivalentPercent();
        Integer better = Math.abs(gap) < TIE ? null : (gap > 0 ? 0 : 1);
        return new FixedIncomeComparison(
                better,
                Math.abs(first.result().netCents() - second.result().netCents()),
                Math.abs(gap));
    }

    private FixedIncomeSimulation run(FixedIncomeInput input, Map<MarketRateCode, BigDecimal> stored) {
        BigDecimal cdi = input.indexer() == Indexer.CDI_PERCENT
                ? resolve(input.cdiAnnualPercent(), stored, MarketRateCode.CDI) : null;
        BigDecimal selic = input.indexer() == Indexer.SELIC_PERCENT
                ? resolve(input.selicAnnualPercent(), stored, MarketRateCode.SELIC) : null;
        BigDecimal ipca = input.indexer() == Indexer.IPCA_PLUS
                ? resolve(input.ipcaAnnualPercent(), stored, MarketRateCode.IPCA) : null;
        LocalDate start = input.startDate() != null ? input.startDate() : clock.today();

        FixedIncomeParams params = new FixedIncomeParams(input.type(), input.indexer(), input.rate(),
                input.initialCents(), input.monthlyContributionCents(), input.months(), start, cdi, selic, ipca);
        return new FixedIncomeSimulation(params, FixedIncomeSimulator.simulate(params));
    }

    private static BigDecimal resolve(BigDecimal given, Map<MarketRateCode, BigDecimal> stored, MarketRateCode code) {
        if (given != null) {
            return given;
        }
        BigDecimal value = stored == null ? null : stored.get(code);
        if (value == null) {
            throw AppException.of(ErrorCode.MARKET_RATE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE,
                    Map.of("rate", code.name()));
        }
        return value;
    }

    private static boolean needsStoredRate(FixedIncomeInput input) {
        return switch (input.indexer()) {
            case CDI_PERCENT -> input.cdiAnnualPercent() == null;
            case SELIC_PERCENT -> input.selicAnnualPercent() == null;
            case IPCA_PLUS -> input.ipcaAnnualPercent() == null;
            case PREFIXED -> false;
        };
    }

    private Map<MarketRateCode, BigDecimal> storedRates() {
        Map<MarketRateCode, BigDecimal> rates = new EnumMap<>(MarketRateCode.class);
        for (MarketRate rate : marketRates.current()) {
            rates.put(rate.getCode(), rate.getRatePercent());
        }
        return rates;
    }
}
