package com.ir.portfolio.application;

import com.ir.portfolio.domain.exception.PtaxUnavailableException;
import com.ir.portfolio.domain.model.PtaxQuote;
import com.ir.portfolio.domain.model.PtaxRateType;
import com.ir.portfolio.infrastructure.bcb.BcbDailyQuote;
import com.ir.portfolio.infrastructure.bcb.BcbPtaxClient;
import com.ir.portfolio.infrastructure.bcb.BcbProperties;
import com.ir.portfolio.infrastructure.persistence.entity.PtaxQuoteEntity;
import com.ir.portfolio.infrastructure.persistence.repository.PtaxQuoteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PtaxServiceTest {

    @Mock
    private PtaxQuoteRepository repository;

    @Mock
    private BcbPtaxClient client;

    private PtaxService service;

    @BeforeEach
    void setUp() {
        BcbProperties properties = new BcbProperties(
                "http://localhost", Duration.ofSeconds(3), Duration.ofSeconds(5), 3);
        service = new PtaxService(repository, client, properties);
    }

    private static BcbDailyQuote bcbQuote(String date, String buy, String sell) {
        return new BcbDailyQuote(new BigDecimal(buy), new BigDecimal(sell), date + " 13:08:33.243");
    }

    @Test
    void exactCacheHitDoesNotCallBcb() {
        LocalDate date = LocalDate.parse("2025-01-10");
        when(repository.findById(date)).thenReturn(Optional.of(
                new PtaxQuoteEntity(date, new BigDecimal("5.08"), new BigDecimal("5.10"), null)));

        PtaxQuote quote = service.get(date, PtaxRateType.SELL);

        assertThat(quote.rate()).isEqualByComparingTo("5.10");
        assertThat(quote.quoteDate()).isEqualTo(date);
        assertThat(quote.fallback()).isFalse();
        verifyNoInteractions(client);
    }

    @Test
    void cacheMissFetchesWindowAndPersistsQuotes() {
        LocalDate date = LocalDate.parse("2025-01-10");
        when(repository.findById(date)).thenReturn(Optional.empty());
        when(repository.existsById(any())).thenReturn(false);
        when(client.quotesBetween(date, date.plusDays(3))).thenReturn(List.of(
                bcbQuote("2025-01-10", "5.08", "5.10"),
                bcbQuote("2025-01-13", "5.12", "5.14")));

        PtaxQuote quote = service.get(date, PtaxRateType.BUY);

        assertThat(quote.rate()).isEqualByComparingTo("5.08");
        assertThat(quote.fallback()).isFalse();
        verify(repository, org.mockito.Mockito.times(2)).save(any(PtaxQuoteEntity.class));
    }

    @Test
    void weekendRequestFallsForwardToNextBusinessDay() {
        LocalDate saturday = LocalDate.parse("2025-01-11");
        when(repository.findById(saturday)).thenReturn(Optional.empty());
        when(repository.existsById(any())).thenReturn(true);
        when(client.quotesBetween(saturday, saturday.plusDays(3))).thenReturn(List.of(
                bcbQuote("2025-01-13", "5.12", "5.14")));

        PtaxQuote quote = service.get(saturday, PtaxRateType.BUY);

        assertThat(quote.rate()).isEqualByComparingTo("5.12");
        assertThat(quote.quoteDate()).isEqualTo(LocalDate.parse("2025-01-13"));
        assertThat(quote.requestedDate()).isEqualTo(saturday);
        assertThat(quote.fallback()).isTrue();
        verify(repository, never()).save(any());
    }

    @Test
    void emptyWindowThrowsPtaxUnavailable() {
        LocalDate date = LocalDate.parse("2025-03-01");
        when(repository.findById(date)).thenReturn(Optional.empty());
        when(client.quotesBetween(date, date.plusDays(3))).thenReturn(List.of());

        assertThatThrownBy(() -> service.get(date, PtaxRateType.BUY))
                .isInstanceOf(PtaxUnavailableException.class);
    }

    @Test
    void fallbackCacheHitOnLaterDateIsMarkedAsFallback() {
        LocalDate saturday = LocalDate.parse("2025-01-11");
        when(repository.findById(saturday)).thenReturn(Optional.empty());
        when(repository.existsById(any())).thenReturn(true);
        when(client.quotesBetween(saturday, saturday.plusDays(3))).thenReturn(List.of(
                bcbQuote("2025-01-13", "5.12", "5.14")));

        PtaxQuote sell = service.get(saturday, PtaxRateType.SELL);
        assertThat(sell.rate()).isEqualByComparingTo("5.14");
        assertThat(sell.fallback()).isTrue();
    }
}
