package com.ir.portfolio.domain.service;

import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.domain.model.LedgerLine;
import com.ir.portfolio.domain.model.OrderInput;
import com.ir.portfolio.domain.model.OrderType;
import com.ir.portfolio.domain.model.PtaxQuote;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.ir.portfolio.domain.service.CalcScale.div;

/**
 * Recomputes, order by order, the accumulated quantity, average price and
 * sale gain/tax of every operation of one asset. Faithful port of the
 * front-end's {@code buildLedger} in {@code app/src/lib/calc.ts}.
 *
 * <p>For US assets a parallel BRL ledger is accumulated operation by operation,
 * each order converted at its own PTAX rate — the BRL average price is never a
 * reconversion of the final USD average price.
 *
 * <p>TAX-SIMPLIFICATION: each profitable SELL is taxed in isolation. Loss
 * compensation across months/operations and the R$ 20,000/month exemption for
 * BR stock sales (both allowed by Receita Federal rules) are intentionally not
 * implemented — future work, kept consistent with the current front-end.
 */
public final class LedgerEngine {

    private static final Comparator<OrderInput> LEDGER_ORDER =
            Comparator.comparing(OrderInput::tradeDate).thenComparingLong(OrderInput::seq);

    private LedgerEngine() {
    }

    public static List<LedgerLine> buildLedger(List<OrderInput> orders, Asset asset) {
        boolean isUs = asset.isUs();
        BigDecimal taxRate = CapitalGainsTaxRate.forAsset(asset);
        List<OrderInput> sorted = orders.stream().sorted(LEDGER_ORDER).toList();

        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal avg = BigDecimal.ZERO;
        BigDecimal avgBrl = BigDecimal.ZERO;

        List<LedgerLine> lines = new ArrayList<>(sorted.size());

        for (int i = 0; i < sorted.size(); i++) {
            OrderInput order = sorted.get(i);
            BigDecimal prevQty = qty;
            BigDecimal prevAvg = avg;
            BigDecimal prevAvgBrl = avgBrl;

            PtaxQuote ptax = null;
            BigDecimal orderValueBrl = null;
            if (isUs) {
                ptax = order.ptax();
                if (ptax == null) {
                    throw new IllegalArgumentException(
                            "US asset " + asset.ticker() + " requires a resolved PTAX quote on order " + order.id());
                }
                orderValueBrl = order.orderValue().multiply(ptax.rate());
            }

            boolean hasQty = order.quantity().signum() != 0;
            BigDecimal pricePerShare = hasQty ? div(order.orderValue(), order.quantity()) : BigDecimal.ZERO;
            BigDecimal pricePerShareBrl = (isUs && hasQty) ? div(orderValueBrl, order.quantity()) : BigDecimal.ZERO;

            BigDecimal newQty = prevQty;
            BigDecimal saleGain = null;
            BigDecimal saleGainBrl = null;

            switch (order.type()) {
                case BUY -> {
                    newQty = prevQty.add(order.quantity());
                    avg = prevQty.signum() == 0
                            ? pricePerShare
                            : div(prevAvg.multiply(prevQty).add(order.orderValue()), newQty);
                    if (isUs) {
                        avgBrl = prevQty.signum() == 0
                                ? pricePerShareBrl
                                : div(prevAvgBrl.multiply(prevQty).add(orderValueBrl), newQty);
                    }
                }
                case BONUS_SHARES -> {
                    newQty = prevQty.add(order.quantity());
                    avg = newQty.signum() == 0
                            ? BigDecimal.ZERO
                            : div(prevAvg.multiply(prevQty).add(order.orderValue()), newQty);
                    if (isUs) {
                        avgBrl = newQty.signum() == 0
                                ? BigDecimal.ZERO
                                : div(prevAvgBrl.multiply(prevQty).add(orderValueBrl), newQty);
                    }
                }
                case SPLIT -> {
                    newQty = prevQty.add(order.quantity());
                    avg = newQty.signum() == 0 ? BigDecimal.ZERO : div(prevAvg.multiply(prevQty), newQty);
                    if (isUs) {
                        avgBrl = newQty.signum() == 0 ? BigDecimal.ZERO : div(prevAvgBrl.multiply(prevQty), newQty);
                    }
                }
                case REVERSE_SPLIT -> {
                    newQty = prevQty.subtract(order.quantity());
                    avg = newQty.signum() == 0 ? BigDecimal.ZERO : div(prevAvg.multiply(prevQty), newQty);
                    if (isUs) {
                        avgBrl = newQty.signum() == 0 ? BigDecimal.ZERO : div(prevAvgBrl.multiply(prevQty), newQty);
                    }
                }
                case AMORTIZATION -> {
                    newQty = prevQty;
                    BigDecimal base = order.quantity().signum() != 0
                            ? order.quantity()
                            : (prevQty.signum() != 0 ? prevQty : BigDecimal.ONE);
                    avg = prevAvg.subtract(div(order.orderValue(), base));
                    if (isUs) {
                        avgBrl = prevAvgBrl.subtract(div(orderValueBrl, base));
                    }
                }
                case SELL -> {
                    newQty = prevQty.subtract(order.quantity());
                    avg = prevAvg;
                    if (isUs) {
                        avgBrl = prevAvgBrl;
                    }
                    saleGain = order.quantity().multiply(pricePerShare.subtract(prevAvg));
                    if (isUs) {
                        saleGainBrl = order.quantity().multiply(pricePerShareBrl.subtract(prevAvgBrl));
                    }
                }
            }

            qty = newQty;

            BigDecimal taxableGainBrl = isUs ? saleGainBrl : saleGain;
            boolean taxable = order.type() == OrderType.SELL
                    && taxableGainBrl != null && taxableGainBrl.signum() > 0;
            BigDecimal taxDue = taxable ? taxableGainBrl.multiply(taxRate) : null;

            lines.add(new LedgerLine(
                    order.id(),
                    i + 1,
                    order.type(),
                    order.tradeDate(),
                    order.quantity(),
                    order.orderValue(),
                    pricePerShare,
                    qty,
                    avg,
                    isUs ? avgBrl : null,
                    orderValueBrl,
                    avg.multiply(qty),
                    isUs ? avgBrl.multiply(qty) : null,
                    saleGain,
                    saleGainBrl,
                    order.type() == OrderType.SELL ? taxRate : null,
                    taxDue,
                    ptax
            ));
        }

        return lines;
    }
}
