package br.com.mkfinance.application.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * "Today" in the app's business timezone (PLANEJAMENTO §5 R7).
 *
 * <p>Reading it from {@code LocalDate.now()} would use the server's default zone, so a business date
 * would land a few hours early or late depending on where the container runs; anchoring it to
 * {@code America/Sao_Paulo} keeps that answer stable across the whole app.
 *
 * <p>It was born in {@code application/card} (statements were the first place the backend needed the
 * current date, to decide whether a bill had closed) and was promoted here by BE-5, which needs the
 * same "today" to default the dashboard's reference month and to derive open statements. Injectable
 * rather than static so a test can wind the clock forward without waiting for the calendar.
 */
@Component
public class BusinessClock {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final Clock clock;

    public BusinessClock() {
        this(Clock.system(ZONE));
    }

    public BusinessClock(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
