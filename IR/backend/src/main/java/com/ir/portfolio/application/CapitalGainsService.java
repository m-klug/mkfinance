package com.ir.portfolio.application;

import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.domain.model.LedgerLine;
import com.ir.portfolio.domain.model.OrderType;
import com.ir.portfolio.domain.service.LedgerEngine;
import com.ir.portfolio.infrastructure.persistence.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CapitalGainsService {

    private final AssetService assetService;
    private final OrderService orderService;
    private final OrderInputAssembler assembler;
    private final OrderRepository orderRepository;
    private final UserContext userContext;

    public CapitalGainsService(AssetService assetService, OrderService orderService,
                               OrderInputAssembler assembler, OrderRepository orderRepository,
                               UserContext userContext) {
        this.assetService = assetService;
        this.orderService = orderService;
        this.assembler = assembler;
        this.orderRepository = orderRepository;
        this.userContext = userContext;
    }

    /** Years that have at least one SELL order (drives the front-end's year selector). */
    @Transactional(readOnly = true)
    public List<Integer> yearsWithSales() {
        return orderRepository.findDistinctYearsByUserIdAndOrderType(userContext.currentUserId(), OrderType.SELL);
    }

    /**
     * All SELL ledger lines of the year, across every asset, sorted by trade
     * date. The ledger of each asset is always computed over its FULL order
     * history — only the reporting window is bounded — so the running average
     * price feeding each sale is correct even when shaped by prior years.
     */
    @Transactional(readOnly = true)
    public List<Sale> salesOfYear(int year) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);

        List<Sale> sales = new ArrayList<>();
        for (Asset asset : assetService.list()) {
            List<LedgerLine> ledger = LedgerEngine.buildLedger(
                    assembler.assemble(asset, orderService.listByAsset(asset.id())), asset);
            for (LedgerLine line : ledger) {
                if (line.orderType() == OrderType.SELL
                        && !line.tradeDate().isBefore(start) && !line.tradeDate().isAfter(end)) {
                    sales.add(new Sale(asset, line));
                }
            }
        }
        sales.sort(Comparator.comparing(sale -> sale.line().tradeDate()));
        return sales;
    }

    public record Sale(Asset asset, LedgerLine line) {
    }
}
