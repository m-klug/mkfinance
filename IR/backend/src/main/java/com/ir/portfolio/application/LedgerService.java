package com.ir.portfolio.application;

import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.domain.model.LedgerLine;
import com.ir.portfolio.domain.service.LedgerEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LedgerService {

    private final AssetService assetService;
    private final OrderService orderService;
    private final OrderInputAssembler assembler;

    public LedgerService(AssetService assetService, OrderService orderService, OrderInputAssembler assembler) {
        this.assetService = assetService;
        this.orderService = orderService;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public LedgerResult ledger(String ticker) {
        Asset asset = assetService.getByTicker(ticker);
        List<LedgerLine> lines = LedgerEngine.buildLedger(
                assembler.assemble(asset, orderService.listByAsset(asset.id())), asset);
        return new LedgerResult(asset, lines);
    }

    public record LedgerResult(Asset asset, List<LedgerLine> lines) {
    }
}
