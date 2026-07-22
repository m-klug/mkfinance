package com.ir.portfolio.infrastructure.config;

import com.ir.portfolio.domain.model.PtaxRateType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(@NonNull CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE");
    }

    @Override
    public void addFormatters(@NonNull FormatterRegistry registry) {
        // lets clients pass ?type=buy|sell in lowercase
        registry.addConverter(String.class, PtaxRateType.class,
                source -> PtaxRateType.valueOf(source.trim().toUpperCase()));
    }
}
