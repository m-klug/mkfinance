package br.com.mkfinance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class MkFinanceApplicationTests {

    @Test
    void contextLoadsAndMigrationsApply() {
        // Boots the full context against a real Postgres (Testcontainers),
        // which also validates the Flyway migrations and the JPA setup.
    }
}
