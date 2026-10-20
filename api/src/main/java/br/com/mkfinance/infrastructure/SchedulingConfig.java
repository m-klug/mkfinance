package br.com.mkfinance.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} jobs. */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
