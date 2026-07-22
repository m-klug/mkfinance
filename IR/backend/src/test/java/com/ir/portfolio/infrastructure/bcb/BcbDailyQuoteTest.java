package com.ir.portfolio.infrastructure.bcb;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class BcbDailyQuoteTest {

    @Test
    void parsesTimestampsWithVariableFractionalSeconds() {
        // real BCB payloads vary the fraction length (and may omit it)
        assertThat(quote("2025-06-23 13:09:26.9").timestamp().toLocalDate())
                .isEqualTo(LocalDate.parse("2025-06-23"));
        assertThat(quote("2025-01-10 13:08:33.243").timestamp().toLocalDate())
                .isEqualTo(LocalDate.parse("2025-01-10"));
        assertThat(quote("2025-01-10 13:08:33").timestamp().toLocalDate())
                .isEqualTo(LocalDate.parse("2025-01-10"));
    }

    private static BcbDailyQuote quote(String timestamp) {
        return new BcbDailyQuote(new BigDecimal("5.08"), new BigDecimal("5.10"), timestamp);
    }
}
