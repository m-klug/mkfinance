package com.ir.portfolio.application;

import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.domain.model.OrderInput;
import com.ir.portfolio.domain.model.PtaxQuote;
import com.ir.portfolio.domain.model.PtaxRateType;
import com.ir.portfolio.domain.port.PtaxProvider;
import com.ir.portfolio.domain.service.CapitalGainsTaxRate;
import com.ir.portfolio.infrastructure.persistence.entity.OrderEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps persisted orders to ledger-engine inputs, resolving the PTAX quote of
 * each order for US assets (the engine itself never performs I/O).
 */
@Component
public class OrderInputAssembler {

    private final PtaxProvider ptaxProvider;

    public OrderInputAssembler(PtaxProvider ptaxProvider) {
        this.ptaxProvider = ptaxProvider;
    }

    public List<OrderInput> assemble(Asset asset, List<OrderEntity> orders) {
        boolean isUs = asset.isUs();
        return orders.stream()
                .map(order -> {
                    PtaxQuote ptax = null;
                    if (isUs) {
                        PtaxRateType rateType = CapitalGainsTaxRate.ptaxRateTypeFor(order.getOrderType());
                        ptax = ptaxProvider.get(order.getTradeDate(), rateType);
                    }
                    return new OrderInput(
                            order.getId(),
                            order.getSeq(),
                            order.getOrderType(),
                            order.getTradeDate(),
                            order.getQuantity(),
                            order.getOrderValue(),
                            ptax);
                })
                .toList();
    }
}
