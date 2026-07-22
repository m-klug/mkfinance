package com.ir.portfolio.application;

import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.domain.model.Market;
import com.ir.portfolio.domain.model.Position;
import com.ir.portfolio.domain.service.PositionCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class PositionService {

    private final AssetService assetService;
    private final OrderService orderService;
    private final OrderInputAssembler assembler;

    public PositionService(AssetService assetService, OrderService orderService, OrderInputAssembler assembler) {
        this.assetService = assetService;
        this.orderService = orderService;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public Position position(String ticker, LocalDate cutoffDate) {
        Asset asset = assetService.getByTicker(ticker);
        return PositionCalculator.compute(
                assembler.assemble(asset, orderService.listByAsset(asset.id())), asset, cutoffDate);
    }

    /**
     * Per-asset position today and at the reference date, plus BRL portfolio
     * totals split Brazil/abroad — totals always from today's positions,
     * mirroring the front-end dashboard.
     */
    @Transactional(readOnly = true)
    public DashboardResult dashboard(LocalDate referenceDate) {
        LocalDate reference = referenceDate != null
                ? referenceDate
                : LocalDate.now().minusYears(1).withMonth(12).withDayOfMonth(31);

        BigDecimal totalCostBrazilBrl = BigDecimal.ZERO;
        BigDecimal totalCostAbroadBrl = BigDecimal.ZERO;
        List<DashboardEntry> entries = new ArrayList<>();

        for (Asset asset : assetService.list()) {
            var inputs = assembler.assemble(asset, orderService.listByAsset(asset.id()));
            Position current = PositionCalculator.compute(inputs, asset, null);
            Position atReference = PositionCalculator.compute(inputs, asset, reference);
            entries.add(new DashboardEntry(asset, current, atReference));

            if (asset.market() == Market.BR) {
                totalCostBrazilBrl = totalCostBrazilBrl.add(current.totalCost());
            } else {
                totalCostAbroadBrl = totalCostAbroadBrl.add(current.totalCostBrl());
            }
        }

        return new DashboardResult(reference, entries, totalCostBrazilBrl, totalCostAbroadBrl);
    }

    public record DashboardEntry(Asset asset, Position current, Position atReference) {
    }

    public record DashboardResult(LocalDate referenceDate, List<DashboardEntry> entries,
                                  BigDecimal totalCostBrazilBrl, BigDecimal totalCostAbroadBrl) {
    }
}
