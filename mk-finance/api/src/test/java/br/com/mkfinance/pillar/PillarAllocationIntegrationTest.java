package br.com.mkfinance.pillar;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.mkfinance.AbstractIntegrationTest;
import br.com.mkfinance.api.auth.CurrentUserResponse;
import br.com.mkfinance.api.auth.RegisterRequest;
import br.com.mkfinance.api.dashboard.PillarAllocationLineResponse;
import br.com.mkfinance.api.dashboard.PillarAllocationReportResponse;
import br.com.mkfinance.api.error.ErrorResponse;
import br.com.mkfinance.api.pillar.PillarAllocationResponse;
import br.com.mkfinance.api.pillar.PillarAllocationsRequest;
import br.com.mkfinance.application.common.BusinessClock;
import br.com.mkfinance.domain.model.Pillar;
import br.com.mkfinance.support.ApiSession;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * BE-6 end to end: the six pillar targets versioned by competency (V13) — GET resolves what was in
 * force for a month, PUT replaces the set from the current month onward without rewriting history.
 */
class PillarAllocationIntegrationTest extends AbstractIntegrationTest {

    private ApiSession session;
    private UUID workspaceId;

    @BeforeEach
    void registerUser() {
        session = newSession();
        session.primeCsrf();
        CurrentUserResponse user = session.post(
                        "/auth/register",
                        new RegisterRequest("Tester", "pillar+" + UUID.randomUUID() + "@example.com", "s3cr3t-password"),
                        CurrentUserResponse.class)
                .getBody();
        workspaceId = user.workspaces().get(0).id();
    }

    @Test
    @DisplayName("GET without month defaults to the current one, seeded 30/10/20/10/25/5")
    void seededDefaults() {
        PillarAllocationResponse[] allocations =
                session.get(path("/pillar-allocations"), PillarAllocationResponse[].class).getBody();

        assertThat(allocations).hasSize(6);
        Map<Pillar, PillarAllocationResponse> byPillar = index(allocations);
        assertThat(byPillar.get(Pillar.FIXED_COSTS).targetPercent()).isEqualTo(30);
        assertThat(byPillar.get(Pillar.COMFORT).targetPercent()).isEqualTo(10);
        assertThat(byPillar.get(Pillar.GOALS).targetPercent()).isEqualTo(20);
        assertThat(byPillar.get(Pillar.PLEASURES).targetPercent()).isEqualTo(10);
        assertThat(byPillar.get(Pillar.FINANCIAL_FREEDOM).targetPercent()).isEqualTo(25);
        assertThat(byPillar.get(Pillar.KNOWLEDGE).targetPercent()).isEqualTo(5);
        // Seeded rows read as in force since always (PillarAllocation.FLOOR_DATE).
        assertThat(allocations[0].effectiveFrom()).isEqualTo(LocalDate.of(2000, 1, 1));
    }

    @Test
    @DisplayName("PUT rejects a set that does not sum to 100")
    void rejectsWrongSum() {
        PillarAllocationsRequest request = requestOf(Map.of(
                Pillar.FIXED_COSTS, 30,
                Pillar.COMFORT, 10,
                Pillar.GOALS, 20,
                Pillar.PLEASURES, 10,
                Pillar.FINANCIAL_FREEDOM, 25,
                Pillar.KNOWLEDGE, 4)); // sums to 99

        ResponseEntity<ErrorResponse> response = session.put(path("/pillar-allocations"), request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("PILLAR_TARGETS_MUST_SUM_100");
        assertThat(response.getBody().params()).containsEntry("sum", 99);
    }

    @Test
    @DisplayName("PUT rejects a request where a pillar is missing (duplicated instead), even if it sums to 100")
    void rejectsIncompletePillarSet() {
        // Six items, but FIXED_COSTS is duplicated and KNOWLEDGE is absent — sums to 100 by accident.
        PillarAllocationsRequest request = new PillarAllocationsRequest(List.of(
                new PillarAllocationsRequest.Item(Pillar.FIXED_COSTS, 35),
                new PillarAllocationsRequest.Item(Pillar.FIXED_COSTS, 5),
                new PillarAllocationsRequest.Item(Pillar.COMFORT, 10),
                new PillarAllocationsRequest.Item(Pillar.GOALS, 20),
                new PillarAllocationsRequest.Item(Pillar.PLEASURES, 10),
                new PillarAllocationsRequest.Item(Pillar.FINANCIAL_FREEDOM, 20)));

        ResponseEntity<ErrorResponse> response = session.put(path("/pillar-allocations"), request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    @DisplayName("PUT applies from the current month onward and never rewrites an earlier competency")
    void appliesFromCurrentMonthOnwards() {
        YearMonth currentMonth = YearMonth.now(BusinessClock.ZONE);
        YearMonth previousMonth = currentMonth.minusMonths(1);

        PillarAllocationsRequest request = requestOf(Map.of(
                Pillar.FIXED_COSTS, 40,
                Pillar.COMFORT, 5,
                Pillar.GOALS, 15,
                Pillar.PLEASURES, 15,
                Pillar.FINANCIAL_FREEDOM, 20,
                Pillar.KNOWLEDGE, 5));

        PillarAllocationResponse[] updated =
                session.put(path("/pillar-allocations"), request, PillarAllocationResponse[].class)
                        .getBody();
        assertThat(updated).hasSize(6);
        Map<Pillar, PillarAllocationResponse> byPillar = index(updated);
        assertThat(byPillar.get(Pillar.FIXED_COSTS).targetPercent()).isEqualTo(40);
        assertThat(byPillar.get(Pillar.FIXED_COSTS).effectiveFrom()).isEqualTo(currentMonth.atDay(1));

        // The past competency keeps reading the old, seeded targets.
        PillarAllocationResponse[] previous = session.get(
                        path("/pillar-allocations?month=" + previousMonth), PillarAllocationResponse[].class)
                .getBody();
        assertThat(index(previous).get(Pillar.FIXED_COSTS).targetPercent()).isEqualTo(30);

        // The current competency reads the new targets.
        PillarAllocationResponse[] current = session.get(
                        path("/pillar-allocations?month=" + currentMonth), PillarAllocationResponse[].class)
                .getBody();
        assertThat(index(current).get(Pillar.FIXED_COSTS).targetPercent()).isEqualTo(40);

        // The allocation report for the previous month is judged against the old target too.
        PillarAllocationReportResponse previousReport = session.get(
                        path("/dashboard/pillar-allocation?month=" + previousMonth), PillarAllocationReportResponse.class)
                .getBody();
        assertThat(targetPercentOf(previousReport, Pillar.FIXED_COSTS)).isEqualTo(30);

        // While the current month's report already reflects the edit.
        PillarAllocationReportResponse currentReport = session.get(
                        path("/dashboard/pillar-allocation?month=" + currentMonth), PillarAllocationReportResponse.class)
                .getBody();
        assertThat(targetPercentOf(currentReport, Pillar.FIXED_COSTS)).isEqualTo(40);
    }

    @Test
    @DisplayName("a second PUT within the same competency updates the row in place, not a new one")
    void secondEditWithinTheSameMonthUpdatesInPlace() {
        session.put(
                path("/pillar-allocations"),
                requestOf(Map.of(
                        Pillar.FIXED_COSTS, 40,
                        Pillar.COMFORT, 5,
                        Pillar.GOALS, 15,
                        Pillar.PLEASURES, 15,
                        Pillar.FINANCIAL_FREEDOM, 20,
                        Pillar.KNOWLEDGE, 5)),
                PillarAllocationResponse[].class);

        PillarAllocationResponse[] second = session.put(
                        path("/pillar-allocations"),
                        requestOf(Map.of(
                                Pillar.FIXED_COSTS, 35,
                                Pillar.COMFORT, 10,
                                Pillar.GOALS, 15,
                                Pillar.PLEASURES, 15,
                                Pillar.FINANCIAL_FREEDOM, 20,
                                Pillar.KNOWLEDGE, 5)),
                        PillarAllocationResponse[].class)
                .getBody();

        assertThat(index(second).get(Pillar.FIXED_COSTS).targetPercent()).isEqualTo(35);

        PillarAllocationResponse[] current =
                session.get(path("/pillar-allocations"), PillarAllocationResponse[].class).getBody();
        // Still 6 rows in force — the second edit replaced the first, it did not stack a new row.
        assertThat(current).hasSize(6);
        assertThat(index(current).get(Pillar.FIXED_COSTS).targetPercent()).isEqualTo(35);
    }

    // --- helpers ---

    private String path(String suffix) {
        return "/workspaces/" + workspaceId + suffix;
    }

    private PillarAllocationsRequest requestOf(Map<Pillar, Integer> targets) {
        List<PillarAllocationsRequest.Item> items = targets.entrySet().stream()
                .map(e -> new PillarAllocationsRequest.Item(e.getKey(), e.getValue()))
                .toList();
        return new PillarAllocationsRequest(items);
    }

    private Map<Pillar, PillarAllocationResponse> index(PillarAllocationResponse[] allocations) {
        return java.util.Arrays.stream(allocations)
                .collect(java.util.stream.Collectors.toMap(PillarAllocationResponse::pillar, a -> a));
    }

    private int targetPercentOf(PillarAllocationReportResponse report, Pillar pillar) {
        return report.pillars().stream()
                .filter(p -> p.pillar() == pillar)
                .findFirst()
                .map(PillarAllocationLineResponse::targetPercent)
                .orElseThrow();
    }
}
