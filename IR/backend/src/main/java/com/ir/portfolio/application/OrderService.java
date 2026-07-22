package com.ir.portfolio.application;

import com.ir.portfolio.domain.exception.BusinessRuleException;
import com.ir.portfolio.domain.exception.ErrorCode;
import com.ir.portfolio.domain.exception.ResourceNotFoundException;
import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.domain.model.OrderType;
import com.ir.portfolio.infrastructure.persistence.entity.OrderEntity;
import com.ir.portfolio.infrastructure.persistence.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderService {

    /** Order types whose orderValue may legitimately be zero. */
    private static final Set<OrderType> ZERO_VALUE_ALLOWED =
            Set.of(OrderType.SPLIT, OrderType.REVERSE_SPLIT, OrderType.BONUS_SHARES);

    private final OrderRepository orderRepository;
    private final AssetService assetService;
    private final UserContext userContext;

    public OrderService(OrderRepository orderRepository, AssetService assetService, UserContext userContext) {
        this.orderRepository = orderRepository;
        this.assetService = assetService;
        this.userContext = userContext;
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> list(String ticker) {
        if (ticker == null || ticker.isBlank()) {
            return orderRepository.findByUserIdOrderByTradeDateAscSeqAsc(userContext.currentUserId());
        }
        Asset asset = assetService.getByTicker(ticker);
        return orderRepository.findByAssetIdOrderByTradeDateAscSeqAsc(asset.id());
    }

    @Transactional(readOnly = true)
    public List<OrderEntity> listByAsset(UUID assetId) {
        return orderRepository.findByAssetIdOrderByTradeDateAscSeqAsc(assetId);
    }

    @Transactional
    public OrderEntity create(String ticker, OrderType type, LocalDate tradeDate,
                              BigDecimal quantity, BigDecimal orderValue) {
        Asset asset = assetService.getByTicker(ticker);
        validateOrderValue(type, orderValue);

        if (type == OrderType.SELL) {
            List<OrderEntity> existing = orderRepository.findByAssetIdOrderByTradeDateAscSeqAsc(asset.id());
            BigDecimal available = quantityAt(existing, tradeDate);
            if (quantity.compareTo(available) > 0) {
                throw new BusinessRuleException(
                        ErrorCode.INSUFFICIENT_QUANTITY,
                        "Quantidade indisponível para venda em %s: disponível %s, solicitado %s"
                                .formatted(tradeDate, available.stripTrailingZeros().toPlainString(),
                                        quantity.stripTrailingZeros().toPlainString()),
                        Map.of("available", available));
            }
        }

        return orderRepository.save(new OrderEntity(
                userContext.currentUserId(), asset.id(), type, tradeDate, quantity, orderValue));
    }

    @Transactional
    public OrderEntity update(UUID orderId, OrderType type, LocalDate tradeDate,
                              BigDecimal quantity, BigDecimal orderValue) {
        OrderEntity order = getOwned(orderId);

        OrderType newType = type != null ? type : order.getOrderType();
        LocalDate newDate = tradeDate != null ? tradeDate : order.getTradeDate();
        BigDecimal newQuantity = quantity != null ? quantity : order.getQuantity();
        BigDecimal newValue = orderValue != null ? orderValue : order.getOrderValue();

        validateOrderValue(newType, newValue);

        List<OrderEntity> history = new ArrayList<>(
                orderRepository.findByAssetIdOrderByTradeDateAscSeqAsc(order.getAssetId()));
        order.update(newType, newDate, newQuantity, newValue);
        validateHistory(history);

        return order;
    }

    @Transactional
    public void delete(UUID orderId) {
        OrderEntity order = getOwned(orderId);
        List<OrderEntity> remaining = orderRepository.findByAssetIdOrderByTradeDateAscSeqAsc(order.getAssetId())
                .stream()
                .filter(o -> !o.getId().equals(orderId))
                .toList();
        validateHistory(remaining);
        orderRepository.delete(order);
    }

    private OrderEntity getOwned(UUID orderId) {
        return orderRepository.findById(orderId)
                .filter(o -> o.getUserId().equals(userContext.currentUserId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ORDER_NOT_FOUND, "Ordem não encontrada: " + orderId));
    }

    private static void validateOrderValue(OrderType type, BigDecimal orderValue) {
        if (orderValue.signum() == 0 && !ZERO_VALUE_ALLOWED.contains(type)) {
            throw new BusinessRuleException(
                    ErrorCode.ORDER_VALUE_REQUIRED,
                    "Valor da ordem é obrigatório para o tipo " + type);
        }
    }

    /**
     * Accumulated quantity up to a cutoff date (inclusive), considering only
     * quantity effects — prices and PTAX are irrelevant for availability, which
     * keeps write-path validation independent of the BCB integration.
     */
    private static BigDecimal quantityAt(List<OrderEntity> orders, LocalDate cutoff) {
        BigDecimal qty = BigDecimal.ZERO;
        for (OrderEntity order : sortedForLedger(orders)) {
            if (order.getTradeDate().isAfter(cutoff)) {
                break;
            }
            qty = qty.add(quantityDelta(order));
        }
        return qty;
    }

    /** Rejects an edited/reduced history in which the running quantity would ever go negative. */
    private static void validateHistory(List<OrderEntity> orders) {
        BigDecimal qty = BigDecimal.ZERO;
        for (OrderEntity order : sortedForLedger(orders)) {
            qty = qty.add(quantityDelta(order));
            if (qty.signum() < 0) {
                throw new BusinessRuleException(
                        ErrorCode.INVALID_ORDER_HISTORY,
                        "Alteração rejeitada: a quantidade acumulada ficaria negativa em %s (%s)"
                                .formatted(order.getTradeDate(), qty.stripTrailingZeros().toPlainString()));
            }
        }
    }

    private static List<OrderEntity> sortedForLedger(List<OrderEntity> orders) {
        return orders.stream()
                .sorted(Comparator.comparing(OrderEntity::getTradeDate).thenComparing(OrderEntity::getSeq))
                .toList();
    }

    private static BigDecimal quantityDelta(OrderEntity order) {
        return switch (order.getOrderType()) {
            case BUY, BONUS_SHARES, SPLIT -> order.getQuantity();
            case SELL, REVERSE_SPLIT -> order.getQuantity().negate();
            case AMORTIZATION -> BigDecimal.ZERO;
        };
    }
}
