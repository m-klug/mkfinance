package com.ir.portfolio.infrastructure.bcb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ir.portfolio.domain.exception.PtaxServiceUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Client for the BCB Olinda PTAX OData service. The period endpoint returns
 * one row per business day; weekends and holidays simply have no rows.
 */
@Component
public class BcbPtaxClient {

    private static final DateTimeFormatter BCB_DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    private final RestClient restClient;

    public BcbPtaxClient(RestClient bcbRestClient) {
        this.restClient = bcbRestClient;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BcbResponse(List<BcbDailyQuote> value) {
    }

    /** All PTAX quotes with quote date in [start, end], ascending. Retries once on IO errors. */
    public List<BcbDailyQuote> quotesBetween(LocalDate start, LocalDate end) {
        try {
            return fetch(start, end);
        } catch (RestClientException first) {
            try {
                return fetch(start, end);
            } catch (RestClientException second) {
                throw new PtaxServiceUnavailableException(second);
            }
        }
    }

    private List<BcbDailyQuote> fetch(LocalDate start, LocalDate end) {
        BcbResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/CotacaoDolarPeriodo(dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)")
                        .queryParam("@dataInicial", "'" + BCB_DATE.format(start) + "'")
                        .queryParam("@dataFinalCotacao", "'" + BCB_DATE.format(end) + "'")
                        .queryParam("$format", "json")
                        .queryParam("$select", "cotacaoCompra,cotacaoVenda,dataHoraCotacao")
                        .build())
                .retrieve()
                .body(BcbResponse.class);
        return response == null || response.value() == null ? List.of() : response.value();
    }
}
