package br.com.mkfinance;

import br.com.mkfinance.support.ApiSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;

/**
 * Base for full-stack integration tests: real Postgres via Testcontainers, real servlet
 * container (RANDOM_PORT) so cookies/CSRF/session go through the actual Spring Security filter
 * chain — not MockMvc shortcuts that bypass it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected TestRestTemplate restTemplate;

    protected ApiSession newSession() {
        return new ApiSession(restTemplate);
    }
}
