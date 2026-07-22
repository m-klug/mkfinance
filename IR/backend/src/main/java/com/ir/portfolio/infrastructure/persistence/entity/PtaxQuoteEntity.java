package com.ir.portfolio.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ptax_quote")
public class PtaxQuoteEntity {

    @Id
    @Column(name = "quote_date")
    private LocalDate quoteDate;

    @Column(name = "buy_rate", nullable = false)
    private BigDecimal buyRate;

    @Column(name = "sell_rate", nullable = false)
    private BigDecimal sellRate;

    @Column(name = "bcb_timestamp")
    private OffsetDateTime bcbTimestamp;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected PtaxQuoteEntity() {
    }

    public PtaxQuoteEntity(LocalDate quoteDate, BigDecimal buyRate, BigDecimal sellRate, OffsetDateTime bcbTimestamp) {
        this.quoteDate = quoteDate;
        this.buyRate = buyRate;
        this.sellRate = sellRate;
        this.bcbTimestamp = bcbTimestamp;
    }

    public LocalDate getQuoteDate() {
        return quoteDate;
    }

    public BigDecimal getBuyRate() {
        return buyRate;
    }

    public BigDecimal getSellRate() {
        return sellRate;
    }

    public OffsetDateTime getBcbTimestamp() {
        return bcbTimestamp;
    }
}
