package br.com.mkfinance.application.marketrate;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Brings the daily index history up to date every morning, after the market-rate refresh. */
@Component
@RequiredArgsConstructor
public class IndexHistoryScheduler {

    private final IndexHistoryService service;

    @Scheduled(cron = "0 30 8 * * *", zone = "America/Sao_Paulo")
    void refreshDaily() {
        service.refreshAll();
    }
}
