package com.ir.portfolio.infrastructure.persistence.entity;

import com.ir.portfolio.domain.model.OrderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "investment_order")
public class OrderEntity {

    @Id
    @UuidGenerator
    private UUID id;

    /** DB identity column; global insertion-order tiebreak for the ledger sort. */
    @Generated(event = EventType.INSERT)
    @Column(insertable = false, updatable = false)
    private Long seq;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false)
    private OrderType orderType;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "order_value", nullable = false)
    private BigDecimal orderValue;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected OrderEntity() {
    }

    public OrderEntity(UUID userId, UUID assetId, OrderType orderType, LocalDate tradeDate,
                       BigDecimal quantity, BigDecimal orderValue) {
        this.userId = userId;
        this.assetId = assetId;
        this.orderType = orderType;
        this.tradeDate = tradeDate;
        this.quantity = quantity;
        this.orderValue = orderValue;
    }

    public void update(OrderType orderType, LocalDate tradeDate, BigDecimal quantity, BigDecimal orderValue) {
        this.orderType = orderType;
        this.tradeDate = tradeDate;
        this.quantity = quantity;
        this.orderValue = orderValue;
        this.updatedAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public Long getSeq() {
        return seq;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public LocalDate getTradeDate() {
        return tradeDate;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getOrderValue() {
        return orderValue;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
