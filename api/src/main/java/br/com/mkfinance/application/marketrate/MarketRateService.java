package br.com.mkfinance.application.marketrate;

import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.domain.model.MarketRate;
import br.com.mkfinance.domain.model.MarketRateCode;
import br.com.mkfinance.infrastructure.persistence.MarketRateRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Keeps the CDI, Selic and IPCA the fixed-income simulator suggests, and serves them from the
 * database. The Banco Central is only asked when a stored value is missing or older than
 * {@link #MAX_AGE}; if it cannot be reached the last stored value is served as is, so the
 * simulator never goes down with the provider.
 *
 * <p>After a failed fetch the same rate is not asked again for {@code retryAfter}: the provider
 * is known to time out for tens of seconds, and every read would otherwise wait for it.
 *
 * <p>Refreshing is serialized: two requests arriving on a cold start would otherwise both fetch
 * and race on the unique {@code (code, reference_date)} index.
 */
@Service
@Slf4j
public class MarketRateService {

    /** CDI and Selic change at most once a business day; this is a cheap, safe refresh interval. */
    static final Duration MAX_AGE = Duration.ofHours(6);

    private final MarketRateRepository repository;
    private final MarketRateSource source;
    private final BusinessClock clock;
    private final Duration retryAfter;
    private final Map<MarketRateCode, Instant> lastFailure = new EnumMap<>(MarketRateCode.class);

    public MarketRateService(
            MarketRateRepository repository,
            MarketRateSource source,
            BusinessClock clock,
            @Value("${mkfinance.market-rates.retry-after:PT10M}") Duration retryAfter) {
        this.repository = repository;
        this.source = source;
        this.clock = clock;
        this.retryAfter = retryAfter;
    }

    /** The current value of each known rate, refreshing the stale ones first. Unavailable rates are left out. */
    public List<MarketRate> current() {
        refreshStale();
        List<MarketRate> rates = new ArrayList<>();
        for (MarketRateCode code : MarketRateCode.values()) {
            repository.findFirstByCodeOrderByReferenceDateDesc(code).ifPresent(rates::add);
        }
        return rates;
    }

    /** Fetches every rate regardless of age; used by the daily job. */
    public synchronized void refreshAll() {
        for (MarketRateCode code : MarketRateCode.values()) {
            refresh(code);
        }
    }

    private synchronized void refreshStale() {
        OffsetDateTime threshold = OffsetDateTime.now().minus(MAX_AGE);
        for (MarketRateCode code : MarketRateCode.values()) {
            Optional<MarketRate> stored = repository.findFirstByCodeOrderByReferenceDateDesc(code);
            boolean stale = stored.isEmpty() || stored.get().getFetchedAt().isBefore(threshold);
            if (stale && !failedRecently(code)) {
                refresh(code);
            }
        }
    }

    private boolean failedRecently(MarketRateCode code) {
        Instant failedAt = lastFailure.get(code);
        return failedAt != null && failedAt.plus(retryAfter).isAfter(Instant.now());
    }

    /** One failing rate must not stop the others, nor the caller. */
    private void refresh(MarketRateCode code) {
        try {
            source.latest(code, clock.today()).ifPresent(quote -> store(code, quote));
            lastFailure.remove(code);
        } catch (RuntimeException e) {
            lastFailure.put(code, Instant.now());
            log.warn("Could not refresh market rate {}: {}", code, e.toString());
        }
    }

    private void store(MarketRateCode code, MarketRateQuote quote) {
        MarketRate row = repository.findByCodeAndReferenceDate(code, quote.referenceDate())
                .orElseGet(() -> MarketRate.builder().code(code).referenceDate(quote.referenceDate()).build());
        row.setRatePercent(quote.ratePercent());
        row.setFetchedAt(OffsetDateTime.now());
        repository.save(row);
    }
}
