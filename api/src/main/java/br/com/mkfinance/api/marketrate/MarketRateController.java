package br.com.mkfinance.api.marketrate;

import br.com.mkfinance.application.marketrate.MarketRateService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contract tag {@code marketRates}. Public market data, not tied to a workspace, so there is no
 * workspace in the path; it still requires a signed-in user like everything outside {@code /auth}.
 */
@RestController
@RequestMapping("/market-rates")
@RequiredArgsConstructor
public class MarketRateController {

    private final MarketRateService service;

    @GetMapping
    public List<MarketRateResponse> list() {
        return service.current().stream().map(MarketRateResponse::of).toList();
    }
}
