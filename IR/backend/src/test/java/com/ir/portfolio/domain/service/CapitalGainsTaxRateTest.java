package com.ir.portfolio.domain.service;

import com.ir.portfolio.domain.model.Asset;
import com.ir.portfolio.domain.model.AssetCategory;
import com.ir.portfolio.domain.model.Market;
import com.ir.portfolio.domain.model.OrderType;
import com.ir.portfolio.domain.model.PtaxRateType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CapitalGainsTaxRateTest {

    private static Asset asset(Market market, AssetCategory category) {
        return new Asset(UUID.randomUUID(), "X", "X", market, category);
    }

    @Test
    void usAssetsAreTaxedAt15PercentRegardlessOfCategory() {
        assertThat(CapitalGainsTaxRate.forAsset(asset(Market.US, AssetCategory.STOCK_US))).isEqualByComparingTo("0.15");
        assertThat(CapitalGainsTaxRate.forAsset(asset(Market.US, AssetCategory.REIT_US))).isEqualByComparingTo("0.15");
        assertThat(CapitalGainsTaxRate.forAsset(asset(Market.US, AssetCategory.ETF_US))).isEqualByComparingTo("0.15");
    }

    @Test
    void brFiiIsTaxedAt20Percent() {
        assertThat(CapitalGainsTaxRate.forAsset(asset(Market.BR, AssetCategory.FII))).isEqualByComparingTo("0.20");
    }

    @Test
    void otherBrCategoriesAreTaxedAt15Percent() {
        assertThat(CapitalGainsTaxRate.forAsset(asset(Market.BR, AssetCategory.STOCK_BR))).isEqualByComparingTo("0.15");
        assertThat(CapitalGainsTaxRate.forAsset(asset(Market.BR, AssetCategory.ETF_BR))).isEqualByComparingTo("0.15");
        assertThat(CapitalGainsTaxRate.forAsset(asset(Market.BR, AssetCategory.UNIT))).isEqualByComparingTo("0.15");
    }

    @Test
    void buyOrdersUsePtaxSellRate() {
        assertThat(CapitalGainsTaxRate.ptaxRateTypeFor(OrderType.BUY)).isEqualTo(PtaxRateType.SELL);
    }

    @ParameterizedTest
    @EnumSource(value = OrderType.class, names = "BUY", mode = EnumSource.Mode.EXCLUDE)
    void allOtherOrderTypesUsePtaxBuyRate(OrderType type) {
        assertThat(CapitalGainsTaxRate.ptaxRateTypeFor(type)).isEqualTo(PtaxRateType.BUY);
    }
}
