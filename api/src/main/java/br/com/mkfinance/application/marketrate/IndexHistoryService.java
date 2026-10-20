package br.com.mkfinance.application.marketrate;

import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.domain.model.IndexCode;
import br.com.mkfinance.domain.model.IndexDaily;
import br.com.mkfinance.infrastructure.persistence.IndexDailyRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Keeps the daily CDI and Selic, and serves them from the database. What a post-fixed bond has
 * yielded "until today" depends on what the index really was on each business day, so the history
 * has to be local: the provider is unstable and the same days are read over and over.
 *
 * <p>Coverage grows on demand: asking for a range from a date before anything stored backfills the
 * missing past once, and the days since the last stored one are fetched at most once per
 * {@link #CHECK_EVERY} (a day's rate is published after the day, so asking again sooner finds
 * nothing). A failed fetch is not retried for {@code retryAfter}, and never stops a read: what is
 * stored is served as it is.
 *
 * <p>Serialized, like {@link MarketRateService}: two cold requests would otherwise both fetch and
 * race on the unique {@code (code, reference_date)} index.
 */
@Service
@Slf4j
public class IndexHistoryService {

    static final Duration CHECK_EVERY = Duration.ofHours(6);

    private final IndexDailyRepository repository;
    private final IndexHistorySource source;
    private final BusinessClock clock;
    private final Duration retryAfter;

    private final Map<IndexCode, Instant> lastCheck = new EnumMap<>(IndexCode.class);
    private final Map<IndexCode, Instant> lastFailure = new EnumMap<>(IndexCode.class);
    /** The earliest date already requested from the provider, so an empty past is not asked again. */
    private final Map<IndexCode, LocalDate> backfilledFrom = new EnumMap<>(IndexCode.class);

    public IndexHistoryService(
            IndexDailyRepository repository,
            IndexHistorySource source,
            BusinessClock clock,
            @Value("${mkfinance.market-rates.retry-after:PT10M}") Duration retryAfter) {
        this.repository = repository;
        this.source = source;
        this.clock = clock;
        this.retryAfter = retryAfter;
    }

    /** The stored percent-per-day of each business day in {@code [from, to]}, after extending the coverage to {@code from}. */
    public NavigableMap<LocalDate, BigDecimal> dailyPercents(IndexCode code, LocalDate from, LocalDate to) {
        ensureCoverage(code, from);
        NavigableMap<LocalDate, BigDecimal> series = new TreeMap<>();
        for (IndexDaily day : repository.findByCodeAndReferenceDateBetweenOrderByReferenceDateAsc(code, from, to)) {
            series.put(day.getReferenceDate(), day.getDailyPercent());
        }
        return series;
    }

    /** Makes the stored history reach back to {@code from} and forward to today, as far as the provider answers. */
    public synchronized void ensureCoverage(IndexCode code, LocalDate from) {
        if (failedRecently(code)) {
            return;
        }
        LocalDate today = clock.today();
        try {
            Optional<IndexDaily> oldest = repository.findFirstByCodeOrderByReferenceDateAsc(code);
            Optional<IndexDaily> newest = repository.findFirstByCodeOrderByReferenceDateDesc(code);
            if (oldest.isEmpty()) {
                fetchAndStore(code, from, today);
                backfilledFrom.put(code, from);
                lastCheck.put(code, Instant.now());
            } else {
                LocalDate attempted = backfilledFrom.get(code);
                LocalDate known = attempted != null && attempted.isBefore(oldest.get().getReferenceDate())
                        ? attempted : oldest.get().getReferenceDate();
                if (from.isBefore(known)) {
                    fetchAndStore(code, from, known.minusDays(1));
                    backfilledFrom.put(code, from);
                }
                if (newest.get().getReferenceDate().isBefore(today) && !checkedRecently(code)) {
                    fetchAndStore(code, newest.get().getReferenceDate().plusDays(1), today);
                    lastCheck.put(code, Instant.now());
                }
            }
            lastFailure.remove(code);
        } catch (RuntimeException e) {
            lastFailure.put(code, Instant.now());
            log.warn("Could not extend the {} history: {}", code, e.toString());
        }
    }

    /** Daily job: brings every index that already has history up to today. */
    public synchronized void refreshAll() {
        for (IndexCode code : IndexCode.values()) {
            Optional<IndexDaily> oldest = repository.findFirstByCodeOrderByReferenceDateAsc(code);
            if (oldest.isEmpty()) {
                continue;
            }
            lastCheck.remove(code);
            lastFailure.remove(code);
            ensureCoverage(code, oldest.get().getReferenceDate());
        }
    }

    private boolean failedRecently(IndexCode code) {
        Instant failedAt = lastFailure.get(code);
        return failedAt != null && failedAt.plus(retryAfter).isAfter(Instant.now());
    }

    private boolean checkedRecently(IndexCode code) {
        Instant checkedAt = lastCheck.get(code);
        return checkedAt != null && checkedAt.plus(CHECK_EVERY).isAfter(Instant.now());
    }

    /** Stores what the provider returns for {@code [from, to]}, skipping days already stored. */
    private void fetchAndStore(IndexCode code, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            return;
        }
        List<IndexDailyQuote> quotes = source.between(code, from, to);
        Set<LocalDate> stored = repository.findByCodeAndReferenceDateBetweenOrderByReferenceDateAsc(code, from, to).stream()
                .map(IndexDaily::getReferenceDate)
                .collect(Collectors.toSet());
        OffsetDateTime now = OffsetDateTime.now();
        List<IndexDaily> fresh = quotes.stream()
                .filter(quote -> !stored.contains(quote.date()))
                .map(quote -> IndexDaily.builder()
                        .code(code)
                        .referenceDate(quote.date())
                        .dailyPercent(quote.dailyPercent())
                        .fetchedAt(now)
                        .build())
                .toList();
        repository.saveAll(fresh);
    }
}
