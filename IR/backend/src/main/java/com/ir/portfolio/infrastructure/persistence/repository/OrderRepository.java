package com.ir.portfolio.infrastructure.persistence.repository;

import com.ir.portfolio.domain.model.OrderType;
import com.ir.portfolio.infrastructure.persistence.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findByAssetIdOrderByTradeDateAscSeqAsc(UUID assetId);

    List<OrderEntity> findByUserIdOrderByTradeDateAscSeqAsc(UUID userId);

    @Query("""
            select distinct year(o.tradeDate) from OrderEntity o
            where o.userId = :userId and o.orderType = :orderType
            order by year(o.tradeDate)
            """)
    List<Integer> findDistinctYearsByUserIdAndOrderType(@Param("userId") UUID userId,
                                                        @Param("orderType") OrderType orderType);
}
