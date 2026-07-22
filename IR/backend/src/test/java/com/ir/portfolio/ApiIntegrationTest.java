package com.ir.portfolio;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.ir.portfolio.infrastructure.persistence.repository.AssetRepository;
import com.ir.portfolio.infrastructure.persistence.repository.OrderRepository;
import com.ir.portfolio.infrastructure.persistence.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest {

    private static final String PERIOD_PATH =
            "/CotacaoDolarPeriodo(dataInicial=@dataInicial,dataFinalCotacao=@dataFinalCotacao)";

    private static final WireMockServer BCB = new WireMockServer(options().dynamicPort());

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeAll
    static void startBcb() {
        BCB.start();
        // AAPL seed order dates, with the same rates the front-end mock pinned (OVERRIDES)
        stubPeriod("01-10-2025", quote("2025-01-10", "5.08", "5.10"));
        stubPeriod("06-20-2025", quote("2025-06-20", "4.95", "4.97"));
        stubPeriod("02-05-2026", quote("2026-02-05", "5.00", "5.02"));
        // O and VOO seed orders (needed by capital-gains/dashboard, which ledger every asset)
        stubPeriod("03-05-2025", quote("2025-03-05", "5.15", "5.17"));
        stubPeriod("07-01-2025", quote("2025-07-01", "5.05", "5.07"));
        // weekend: Saturday 2025-01-11 resolves to Monday 2025-01-13
        stubPeriod("01-11-2025", quote("2025-01-13", "5.12", "5.14"));
        // cache test date
        stubPeriod("03-10-2025", quote("2025-03-10", "5.20", "5.22"));
        // empty window (long holiday run)
        stubPeriod("04-18-2025", "");
        // timeout date
        BCB.stubFor(get(urlPathEqualTo(PERIOD_PATH))
                .withQueryParam("@dataInicial", equalTo("'05-02-2025'"))
                .willReturn(aResponse().withFixedDelay(3000).withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":[]}")));
    }

    @AfterAll
    static void stopBcb() {
        BCB.stop();
    }

    @DynamicPropertySource
    static void bcbProperties(DynamicPropertyRegistry registry) {
        BCB.start();
        registry.add("bcb.ptax.base-url", BCB::baseUrl);
        registry.add("bcb.ptax.read-timeout", () -> "800ms");
    }

    private static void stubPeriod(String startDateBcbFormat, String quotesJson) {
        BCB.stubFor(get(urlPathEqualTo(PERIOD_PATH))
                .withQueryParam("@dataInicial", equalTo("'" + startDateBcbFormat + "'"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":[" + quotesJson + "]}")));
    }

    private static String quote(String date, String buy, String sell) {
        return "{\"cotacaoCompra\":%s,\"cotacaoVenda\":%s,\"dataHoraCotacao\":\"%s 13:08:33.243\"}"
                .formatted(buy, sell, date);
    }

    // --- migrations / seed ---

    @Test
    @Order(1)
    void migrationsSeedDefaultUserAssetsAndOrders() {
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(assetRepository.count()).isEqualTo(8);
        assertThat(orderRepository.count()).isEqualTo(19);
    }

    // --- read endpoints ---

    @Test
    @Order(2)
    void listAssetsReturnsSeed() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/assets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].ticker").value("AAPL"));
    }

    @Test
    @Order(3)
    void itsa4LedgerComputesSaleTax() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/ledger").param("ticker", "ITSA4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[1].averagePrice").value(10.0))
                .andExpect(jsonPath("$[2].saleGain").value(100.00))
                .andExpect(jsonPath("$[2].taxRate").value(0.15))
                .andExpect(jsonPath("$[2].taxDue").value(15.00))
                .andExpect(jsonPath("$[3].averagePrice").value(10.25));
    }

    @Test
    @Order(4)
    void aaplLedgerConvertsWithBcbRates() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/ledger").param("ticker", "AAPL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].averagePriceBrl").value(765.0))
                .andExpect(jsonPath("$[0].ptax.rate").value(5.10))
                .andExpect(jsonPath("$[0].ptax.type").value("SELL"))
                .andExpect(jsonPath("$[1].saleGain").value(150.00))
                .andExpect(jsonPath("$[1].saleGainBrl").value(630.00))
                .andExpect(jsonPath("$[1].taxDue").value(94.50));
    }

    @Test
    @Order(5)
    void hglg11PositionAfterReverseSplit() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/positions").param("ticker", "HGLG11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(72))
                .andExpect(jsonPath("$.totalCost").value(13200.00));
    }

    @Test
    @Order(6)
    void positionAtCutoffDateIsInclusive() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/positions").param("ticker", "ITSA4").param("date", "2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(150))
                .andExpect(jsonPath("$.averagePrice").value(10.0));
    }

    @Test
    @Order(7)
    void unknownTickerReturns404WithCode() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/positions").param("ticker", "NOPE3"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_FOUND"));
    }

    @Test
    @Order(8)
    void capitalGainsYearsIncludeSeedSaleYears() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/capital-gains/years"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(hasItems(2025, 2026)));
    }

    @Test
    @Order(9)
    void capitalGainsReportGroupsByMonthAndSumsTaxes() throws Exception {
        // June 2025: ITSA4 gain 100 (tax 15) + AAPL BRL gain 630 (tax 94.50)
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/capital-gains").param("year", "2025"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2025))
                .andExpect(jsonPath("$.months.length()").value(1))
                .andExpect(jsonPath("$.months[0].month").value("2025-06"))
                .andExpect(jsonPath("$.months[0].sales.length()").value(2))
                .andExpect(jsonPath("$.months[0].totalResultBrl").value(730.00))
                .andExpect(jsonPath("$.months[0].totalTaxBrl").value(109.50))
                .andExpect(jsonPath("$.totalTaxBrl").value(109.50))
                .andExpect(jsonPath("$.lossOperationsCount").value(0));
    }

    @Test
    @Order(10)
    void capitalGains2026UsesAveragePriceShapedByPriorYears() throws Exception {
        // MXRF11 2026 sale: PM shaped by 2025 amortization (10.00 -> 9.80); gain 85, FII 20% -> 17
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/capital-gains").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months[0].sales[0].ticker").value("MXRF11"))
                .andExpect(jsonPath("$.months[0].sales[0].averagePrice").value(9.80))
                .andExpect(jsonPath("$.months[0].sales[0].saleGain").value(85.00))
                .andExpect(jsonPath("$.months[0].sales[0].taxRate").value(0.20))
                .andExpect(jsonPath("$.months[0].sales[0].taxDue").value(17.00));
    }

    @Test
    @Order(11)
    void dashboardSplitsBrazilAndAbroadTotals() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/dashboard").param("referenceDate", "2025-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenceDate").value("2025-12-31"))
                .andExpect(jsonPath("$.positions.length()").value(8))
                .andExpect(jsonPath("$.totalCostBrazilBrl").isNumber())
                .andExpect(jsonPath("$.totalCostAbroadBrl").isNumber());
    }

    // --- PTAX endpoint ---

    @Test
    @Order(12)
    void ptaxWeekendFallsForwardToNextBusinessDay() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/ptax").param("date", "2025-01-11").param("type", "buy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(5.12))
                .andExpect(jsonPath("$.quoteDate").value("2025-01-13"))
                .andExpect(jsonPath("$.requestedDate").value("2025-01-11"))
                .andExpect(jsonPath("$.fallback").value(true));
    }

    @Test
    @Order(13)
    void ptaxSecondRequestIsServedFromCache() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/ptax").param("date", "2025-03-10").param("type", "sell"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(5.22))
                .andExpect(jsonPath("$.fallback").value(false));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/ptax").param("date", "2025-03-10").param("type", "buy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(5.20));

        BCB.verify(1, getRequestedFor(urlPathEqualTo(PERIOD_PATH))
                .withQueryParam("@dataInicial", equalTo("'03-10-2025'")));
    }

    @Test
    @Order(14)
    void ptaxEmptyWindowReturns422() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/ptax").param("date", "2025-04-18").param("type", "buy"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PTAX_UNAVAILABLE"));
    }

    @Test
    @Order(15)
    void ptaxBcbTimeoutReturns502() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/ptax").param("date", "2025-05-02").param("type", "buy"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PTAX_SERVICE_UNAVAILABLE"));
    }

    // --- write endpoints ---

    @Test
    @Order(20)
    void createAssetAndRejectDuplicateTicker() throws Exception {
        mockMvc.perform(post("/api/v1/assets").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker":"ZZZT3","name":"Teste SA","market":"BR","category":"STOCK_BR"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ticker").value("ZZZT3"));

        mockMvc.perform(post("/api/v1/assets").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker":"ZZZT3","name":"Teste SA","market":"BR","category":"STOCK_BR"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKER_ALREADY_EXISTS"));
    }

    @Test
    @Order(21)
    void createOrderValidations() throws Exception {
        // unknown ticker -> 404
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker":"NOPE3","orderType":"BUY","tradeDate":"2024-01-10","quantity":10,"orderValue":100}
                                """))
                .andExpect(status().isNotFound());

        // negative quantity -> 400 with field errors
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker":"ZZZT3","orderType":"BUY","tradeDate":"2024-01-10","quantity":-5,"orderValue":100}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // BUY with zero value -> 422
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker":"ZZZT3","orderType":"BUY","tradeDate":"2024-01-10","quantity":10,"orderValue":0}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORDER_VALUE_REQUIRED"));

        // SELL without position -> 422 with available quantity
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ticker":"ZZZT3","orderType":"SELL","tradeDate":"2024-01-10","quantity":10,"orderValue":100}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_QUANTITY"))
                .andExpect(jsonPath("$.available").value(0));
    }

    @Test
    @Order(22)
    void createPatchAndDeleteOrderWithHistoryRevalidation() throws Exception {
        String buyId = createOrder("""
                {"ticker":"ZZZT3","orderType":"BUY","tradeDate":"2024-01-10","quantity":100,"orderValue":1000}
                """);
        String sellId = createOrder("""
                {"ticker":"ZZZT3","orderType":"SELL","tradeDate":"2024-02-10","quantity":50,"orderValue":600}
                """);

        // shrinking the BUY below the sold quantity must be rejected
        mockMvc.perform(patch("/api/v1/orders/" + buyId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":30}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_HISTORY"));

        // deleting the BUY would orphan the SELL
        mockMvc.perform(delete("/api/v1/orders/" + buyId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_HISTORY"));

        // a valid PATCH is accepted
        mockMvc.perform(patch("/api/v1/orders/" + buyId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":80}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(80));

        // deleting SELL first, then BUY, is fine
        mockMvc.perform(delete("/api/v1/orders/" + sellId)).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/orders/" + buyId)).andExpect(status().isNoContent());
    }

    private String createOrder(String json) throws Exception {
        String body = mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }
}
