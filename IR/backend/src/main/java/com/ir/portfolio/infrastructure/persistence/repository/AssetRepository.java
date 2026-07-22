package com.ir.portfolio.infrastructure.persistence.repository;

import com.ir.portfolio.infrastructure.persistence.entity.AssetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<AssetEntity, UUID> {

    List<AssetEntity> findByUserIdOrderByTickerAsc(UUID userId);

    Optional<AssetEntity> findByUserIdAndTicker(UUID userId, String ticker);

    boolean existsByUserIdAndTicker(UUID userId, String ticker);
}
