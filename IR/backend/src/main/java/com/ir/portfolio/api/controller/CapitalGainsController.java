package com.ir.portfolio.api.controller;

import com.ir.portfolio.api.dto.CapitalGainsDtos.MonthResponse;
import com.ir.portfolio.api.dto.CapitalGainsDtos.SaleResponse;
import com.ir.portfolio.api.dto.CapitalGainsDtos.YearResponse;
import com.ir.portfolio.api.mapper.DtoMappers;
import com.ir.portfolio.application.CapitalGainsService;
import com.ir.portfolio.domain.model.LedgerLine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/capital-gains")
@Tag(name = "Capital gains")
public class CapitalGainsController {

    private static final DateTimeFormatter MONTH_KEY = DateTimeFormatter.ofPattern("yyyy-MM");

    private final CapitalGainsService capitalGainsService;

    public CapitalGainsController(CapitalGainsService capitalGainsService) {
        this.capitalGainsService = capitalGainsService;
    }

    @GetMapping("/years")
    @Operation(summary = "Years that have at least one SELL order")
    public List<Integer> years() {
        return capitalGainsService.yearsWithSales();
    }

    @GetMapping
    @Operation(summary = "Capital gains report of one year, grouped by month, with taxes per sale")
    public YearResponse report(@RequestParam int year) {
        List<CapitalGainsService.Sale> sales = capitalGainsService.salesOfYear(year);

        Map<String, List<SaleResponse>> byMonth = new LinkedHashMap<>();
        for (CapitalGainsService.Sale sale : sales) {
            byMonth.computeIfAbsent(MONTH_KEY.format(sale.line().tradeDate()), k -> new ArrayList<>())
                    .add(toSaleResponse(sale));
        }

        List<MonthResponse> months = byMonth.entrySet().stream()
                .map(entry -> summarizeMonth(entry.getKey(), entry.getValue()))
                .sorted((a, b) -> a.month().compareTo(b.month()))
                .toList();

        BigDecimal totalResult = months.stream().map(MonthResponse::totalResultBrl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTax = months.stream().map(MonthResponse::totalTaxBrl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long lossCount = months.stream().mapToLong(MonthResponse::lossOperationsCount).sum();

        return new YearResponse(year, months, totalResult, totalTax, lossCount);
    }

    private static SaleResponse toSaleResponse(CapitalGainsService.Sale sale) {
        LedgerLine line = sale.line();
        boolean isUs = sale.asset().isUs();
        BigDecimal taxableGainBrl = isUs ? line.saleGainBrl() : line.saleGain();
        return new SaleResponse(
                sale.asset().ticker(),
                sale.asset().name(),
                sale.asset().market(),
                line.tradeDate(),
                DtoMappers.quantity(line.quantity()),
                DtoMappers.price(line.pricePerShare()),
                DtoMappers.price(line.averagePrice()),
                DtoMappers.money(line.saleGain()),
                DtoMappers.money(line.saleGainBrl()),
                DtoMappers.money(taxableGainBrl),
                line.taxRate(),
                DtoMappers.money(line.taxDue()));
    }

    private static MonthResponse summarizeMonth(String month, List<SaleResponse> sales) {
        BigDecimal totalResult = sales.stream().map(SaleResponse::taxableGainBrl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTax = sales.stream().map(SaleResponse::taxDue)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long lossCount = sales.stream().filter(sale -> sale.taxableGainBrl().signum() < 0).count();
        return new MonthResponse(month, sales, totalResult, totalTax, lossCount);
    }
}
