package br.com.mkfinance.fixedincome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.mkfinance.domain.service.fixedincome.BrazilianBusinessDays;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The business-day calendar CDI accrual runs on. Pure unit tests. */
class BrazilianBusinessDaysTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "2026-01-01", // Confraternização
        "2026-02-16", // Carnaval, segunda
        "2026-02-17", // Carnaval, terça
        "2026-04-03", // Sexta-feira Santa
        "2026-04-21", // Tiradentes
        "2026-05-01", // Dia do Trabalho
        "2026-06-04", // Corpus Christi
        "2026-09-07", // Independência
        "2026-10-12", // Nossa Senhora Aparecida
        "2026-11-02", // Finados
        "2026-11-15", // Proclamação da República (domingo, mas segue feriado)
        "2026-11-20", // Consciência Negra
        "2026-12-25", // Natal
        "2027-03-26" // Sexta-feira Santa de 2027 (Páscoa em 28/03)
    })
    @DisplayName("national holidays are not business days")
    void holidaysAreNotBusinessDays(String date) {
        assertThat(BrazilianBusinessDays.isBusinessDay(LocalDate.parse(date))).isFalse();
    }

    @Test
    @DisplayName("weekends are not business days")
    void weekends() {
        assertThat(BrazilianBusinessDays.isBusinessDay(LocalDate.of(2026, 10, 24))).isFalse(); // sábado
        assertThat(BrazilianBusinessDays.isBusinessDay(LocalDate.of(2026, 10, 25))).isFalse(); // domingo
        assertThat(BrazilianBusinessDays.isBusinessDay(LocalDate.of(2026, 10, 26))).isTrue(); // segunda
    }

    @Test
    @DisplayName("20 Nov only became a national holiday in 2024")
    void blackAwarenessDayStartsIn2024() {
        assertThat(BrazilianBusinessDays.isBusinessDay(LocalDate.of(2023, 11, 20))).isTrue();
        assertThat(BrazilianBusinessDays.isBusinessDay(LocalDate.of(2024, 11, 20))).isFalse();
    }

    @Test
    @DisplayName("counts the start day and leaves out the end day")
    void countIsHalfOpen() {
        // qua 1, qui 2, sex 3 (Sexta-feira Santa), sáb, dom, seg 6, ter 7 -> 4 úteis em [1, 8)
        assertThat(BrazilianBusinessDays.countBetween(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 8))).isEqualTo(4);
        assertThat(BrazilianBusinessDays.countBetween(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 1))).isZero();
    }

    @Test
    @DisplayName("a calendar year has about 252 business days")
    void aYearIsAboutTwoFiftyTwo() {
        int days = BrazilianBusinessDays.countBetween(LocalDate.of(2027, 1, 1), LocalDate.of(2028, 1, 1));
        assertThat(days).isBetween(250, 254);
    }

    @Test
    @DisplayName("an end before the start is refused")
    void reversedRange() {
        assertThatThrownBy(() -> BrazilianBusinessDays.countBetween(LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
