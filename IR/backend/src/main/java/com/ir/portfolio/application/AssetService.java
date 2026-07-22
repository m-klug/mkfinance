package com.ir.portfolio.application;

import com.ir.portfolio.domain.exception.BusinessRuleException;
import com.ir.portfolio.domain.exception.ErrorCode;
import com.ir.portfolio.domain.exception.ResourceNotFoundException;
import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.domain.model.AssetCategory;
import com.ir.portfolio.domain.model.Market;
import com.ir.portfolio.infrastructure.persistence.entity.AssetEntity;
import com.ir.portfolio.infrastructure.persistence.repository.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final UserContext userContext;

    public AssetService(AssetRepository assetRepository, UserContext userContext) {
        this.assetRepository = assetRepository;
        this.userContext = userContext;
    }

    @Transactional(readOnly = true)
    public List<Asset> list() {
        return assetRepository.findByUserIdOrderByTickerAsc(userContext.currentUserId()).stream()
                .map(AssetEntity::toDomain)
                .toList();
    }

    @Transactional(readOnly = true)
    public Asset getByTicker(String ticker) {
        return assetRepository.findByUserIdAndTicker(userContext.currentUserId(), ticker)
                .map(AssetEntity::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ASSET_NOT_FOUND, "Ativo não encontrado na carteira: " + ticker));
    }

    @Transactional
    public Asset create(String ticker, String name, Market market, AssetCategory category) {
        if (assetRepository.existsByUserIdAndTicker(userContext.currentUserId(), ticker)) {
            throw new BusinessRuleException(
                    ErrorCode.TICKER_ALREADY_EXISTS, "Ticker já cadastrado na carteira: " + ticker);
        }
        AssetEntity saved = assetRepository.save(
                new AssetEntity(userContext.currentUserId(), ticker, name, market, category));
        return saved.toDomain();
    }
}
