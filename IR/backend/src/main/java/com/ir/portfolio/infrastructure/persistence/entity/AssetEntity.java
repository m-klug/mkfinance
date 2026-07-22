package com.ir.portfolio.infrastructure.persistence.entity;

import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.domain.model.AssetCategory;
import com.ir.portfolio.domain.model.Market;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "asset")
public class AssetEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String ticker;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetCategory category;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AssetEntity() {
    }

    public AssetEntity(UUID userId, String ticker, String name, Market market, AssetCategory category) {
        this.userId = userId;
        this.ticker = ticker;
        this.name = name;
        this.market = market;
        this.category = category;
    }

    public Asset toDomain() {
        return new Asset(id, ticker, name, market, category);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTicker() {
        return ticker;
    }

    public String getName() {
        return name;
    }

    public Market getMarket() {
        return market;
    }

    public AssetCategory getCategory() {
        return category;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
