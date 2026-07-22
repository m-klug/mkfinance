package br.com.mkfinance.application.card;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * "Today" in the app's business timezone (PLANEJAMENTO §5 R7).
 *
 * <p>Statements are the first place the backend needs the current date at all — it decides whether a
 * statement already closed. Reading it from {@code LocalDate.now()} would use the server's default
 * zone, so a bill would appear to close a few hours early or late depending on where the container
 * runs; anchoring it to {@code America/Sao_Paulo} keeps that answer stable.
 *
 * <p>Injectable rather than static so a test can wind the clock forward past a closing date without
 * waiting for the calendar.
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
