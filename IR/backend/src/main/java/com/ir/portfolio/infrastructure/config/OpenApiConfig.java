package com.ir.portfolio.infrastructure.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI portfolioOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Investment Acquisition Cost Tracker (IR) API")
                .description("Average price, ledger and capital-gains tax calculations "
                        + "for BR and US investments, with real BCB PTAX conversion.")
                .version("v1"));
    }
}
