package br.com.mkfinance.infrastructure.marketdata;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The one HTTP door to the Banco Central SGS open API (no key, no cost). Both the current-rate source
 * and the daily-history source go through it, so timeouts and the base URL live in one place. Each
 * series answers a JSON list of {@code {"data": "dd/MM/yyyy", "valor": "13.65"}}.
 *
 * <p>The provider is known to be unstable (timeouts, 502 pages), so the timeouts are short and
 * callers treat any exception as "try again later".
 */
@Component
public class BcbSgsClient {

    static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RestClient client;

    public BcbSgsClient(@Value("${mkfinance.market-rates.bcb-base-url:https://api.bcb.gov.br}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(8_000);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    /** The raw JSON of {@code series} between two dates, both inclusive. */
    public String fetch(int series, LocalDate from, LocalDate to) {
        return client.get()
                .uri("/dados/serie/bcdata.sgs.{series}/dados?formato=json&dataInicial={from}&dataFinal={to}",
                        series, BR_DATE.format(from), BR_DATE.format(to))
                .retrieve()
                .body(String.class);
    }
}
