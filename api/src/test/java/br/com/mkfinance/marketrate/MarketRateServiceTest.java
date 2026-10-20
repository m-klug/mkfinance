package br.com.mkfinance.marketrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.application.marketrate.MarketRateService;
import br.com.mkfinance.application.marketrate.MarketRateSource;
import br.com.mkfinance.infrastructure.persistence.MarketRateRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The pause after a provider failure. Pure unit test: mocks, no Spring, no database. */
class MarketRateServiceTest {

    private final MarketRateRepository repository = mock(MarketRateRepository.class);
    private final MarketRateSource source = mock(MarketRateSource.class);

    private MarketRateService service(Duration retryAfter) {
        when(repository.findFirstByCodeOrderByReferenceDateDesc(any())).thenReturn(Optional.empty());
        when(source.latest(any(), any())).thenThrow(new IllegalStateException("BCB fora do ar"));
        return new MarketRateService(repository, source, new BusinessClock(), retryAfter);
    }

    @Test
    @DisplayName("after a failure the provider is not asked again for the same rate until the pause ends")
    void failureBacksOff() {
        MarketRateService service = service(Duration.ofMinutes(10));

        service.current();
        service.current();
        service.current();

        verify(source, times(3)).latest(any(), any()); // once per rate, only on the first read
    }

    @Test
    @DisplayName("with no pause every read tries again")
    void noPauseRetries() {
        MarketRateService service = service(Duration.ZERO);

        service.current();
        service.current();

        verify(source, times(6)).latest(any(), any());
        assertThat(service.current()).isEmpty();
    }
}
