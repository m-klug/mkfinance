package br.com.mkfinance.marketrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.application.marketrate.IndexDailyQuote;
import br.com.mkfinance.application.marketrate.IndexHistoryService;
import br.com.mkfinance.application.marketrate.IndexHistorySource;
import br.com.mkfinance.domain.model.IndexCode;
import br.com.mkfinance.domain.service.fixedincome.BrazilianBusinessDays;
import br.com.mkfinance.infrastructure.persistence.IndexDailyRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The daily CDI/Selic history against a real Postgres, with the Banco Central replaced by a fake that
 * publishes one value per business day. The retry pause is switched off so each test sees its own
 * provider behaviour.
 */
class IndexHistoryIntegrationTest extends AbstractIntegrationTest {

    /** A fixed "today" (a Wednesday) so ranges do not depend on when the test runs. */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @MockitoBean
    private IndexHistorySource source;

    @MockitoBean
    private BusinessClock clock;

    @Autowired
    private IndexDailyRepository repository;

    /** Built per test: the service keeps in-memory state (pauses, windows already asked) that must not leak. */
    private IndexHistoryService service;

    @BeforeEach
    void prepare() {
        repository.deleteAll();
        reset(source);
        service = new IndexHistoryService(repository, source, clock, Duration.ZERO);
        when(clock.today()).thenReturn(TODAY);
        when(source.between(any(), any(), any())).thenAnswer(invocation -> publish(invocation.getArgument(1), invocation.getArgument(2)));
    }

    /** One quote per business day in the range, valued by the day of the month so each date is distinguishable. */
    private static List<IndexDailyQuote> publish(LocalDate from, LocalDate to) {
        List<IndexDailyQuote> quotes = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (BrazilianBusinessDays.isBusinessDay(day)) {
                quotes.add(new IndexDailyQuote(day, new BigDecimal("0.0" + (500 + day.getDayOfMonth()))));
            }
        }
        return quotes;
    }

    @Test
    @DisplayName("a first read fetches from the requested date to today and serves it")
    void firstReadFetchesRange() {
        Map<LocalDate, BigDecimal> series = service.dailyPercents(IndexCode.CDI, LocalDate.of(2026, 10, 1), TODAY);

        assertThat(series).containsKeys(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 21));
        assertThat(series).doesNotContainKeys(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 12)); // weekend, holiday
        assertThat(series.get(LocalDate.of(2026, 10, 21))).isEqualByComparingTo("0.0521");
        verify(source, times(1)).between(eq(IndexCode.CDI), eq(LocalDate.of(2026, 10, 1)), eq(TODAY));
    }

    @Test
    @DisplayName("the CDI and the Selic are kept apart")
    void indexesAreIndependent() {
        service.dailyPercents(IndexCode.CDI, LocalDate.of(2026, 10, 19), TODAY);

        assertThat(service.dailyPercents(IndexCode.SELIC, LocalDate.of(2026, 10, 19), TODAY)).isNotEmpty();
        verify(source).between(eq(IndexCode.SELIC), any(), any());
    }

    @Test
    @DisplayName("reading again soon does not call the provider again")
    void secondReadUsesStored() {
        service.dailyPercents(IndexCode.CDI, LocalDate.of(2026, 10, 1), TODAY);
        service.dailyPercents(IndexCode.CDI, LocalDate.of(2026, 10, 1), TODAY);
        service.dailyPercents(IndexCode.CDI, LocalDate.of(2026, 10, 5), TODAY);

        verify(source, times(1)).between(any(), any(), any());
    }

    @Test
    @DisplayName("asking for an earlier start backfills only the missing past")
    void backfillsThePast() {
        service.dailyPercents(IndexCode.CDI, LocalDate.of(2026, 10, 14), TODAY);

        service.dailyPercents(IndexCode.CDI, LocalDate.of(2026, 9, 1), TODAY);

        verify(source).between(IndexCode.CDI, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 13));
        assertThat(repository.count()).isEqualTo(publish(LocalDate.of(2026, 9, 1), TODAY).size());
    }

    @Test
    @DisplayName("an earlier start with nothing published is not asked again")
    void emptyPastIsNotRefetched() {
        service.dailyPercents(IndexCode.CDI, LocalDate.of(2026, 10, 14), TODAY);
        // 3 Oct 2026 is a Saturday: nothing is published before the stored range.
        service.dailyPercents(IndexCode.CDI, LocalDate.of(2026, 10, 3), TODAY);
        service.dailyPercents(IndexCode.CDI, LocalDate.of(2026, 10, 3), TODAY);

        verify(source, times(2)).between(any(), any(), any()); // first range + one backfill attempt
    }

    @Test
    @DisplayName("the daily job brings a stored history up to today without duplicating days")
    void refreshAllExtendsForward() {
        when(clock.today()).thenReturn(LocalDate.of(2026, 10, 16));
        service.dailyPercents(IndexCode.CDI, LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 16));
        when(clock.today()).thenReturn(TODAY);

        service.refreshAll();

        verify(source).between(IndexCode.CDI, LocalDate.of(2026, 10, 17), TODAY);
        assertThat(repository.count()).isEqualTo(publish(LocalDate.of(2026, 10, 13), TODAY).size());
    }

    @Test
    @DisplayName("the daily job leaves alone an index that never had history")
    void refreshAllSkipsEmpty() {
        service.refreshAll();

        verify(source, never()).between(any(), any(), any());
    }

    @Test
    @DisplayName("when the provider is down what is stored is still served")
    void providerDownServesStored() {
        when(clock.today()).thenReturn(LocalDate.of(2026, 10, 20));
        service.dailyPercents(IndexCode.CDI, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20));
        when(clock.today()).thenReturn(LocalDate.of(2026, 10, 22));
        doThrow(new IllegalStateException("BCB fora do ar")).when(source).between(any(), any(), any());

        Map<LocalDate, BigDecimal> series = service.dailyPercents(IndexCode.CDI, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 22));

        assertThat(series).containsOnlyKeys(LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 20));
    }

    @Test
    @DisplayName("when the provider is down and nothing is stored the answer is empty, not an error")
    void providerDownAndEmpty() {
        doThrow(new IllegalStateException("BCB fora do ar")).when(source).between(any(), any(), any());

        assertThat(service.dailyPercents(IndexCode.CDI, LocalDate.of(2026, 10, 1), TODAY)).isEmpty();
    }
}
