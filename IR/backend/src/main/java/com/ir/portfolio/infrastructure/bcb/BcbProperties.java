package com.ir.portfolio.infrastructure.bcb;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "bcb.ptax")
public record BcbProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxForwardDays
) {
    public BcbProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
        if (maxForwardDays <= 0) {
            maxForwardDays = 3;
        }
    }
}
