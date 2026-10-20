package br.com.mkfinance.application.marketrate;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Refreshes the market rates every morning; the lazy refresh on read covers a server that was off. */
@Component
@RequiredArgsConstructor
public class MarketRateScheduler {

    private final MarketRateService service;

    @Scheduled(cron = "0 0 8 * * *", zone = "America/Sao_Paulo")
    void refreshDaily() {
        service.refreshAll();
    }
}
