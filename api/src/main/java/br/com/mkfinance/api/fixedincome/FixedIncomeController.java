package br.com.mkfinance.api.fixedincome;

import br.com.mkfinance.application.fixedincome.FixedIncomeInput;
import br.com.mkfinance.application.fixedincome.FixedIncomeSimulation;
import br.com.mkfinance.application.fixedincome.FixedIncomeSimulationService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Contract tag {@code fixedIncome}. A calculator, not a resource: it creates nothing, so it answers
 * 200 and is not tied to a workspace.
 */
@RestController
@RequestMapping("/fixed-income")
@RequiredArgsConstructor
public class FixedIncomeController {

    private final FixedIncomeSimulationService service;

    @PostMapping("/simulations")
    public FixedIncomeSimulationResponse simulate(@Valid @RequestBody FixedIncomeSimulationRequest request) {
        List<FixedIncomeInput> inputs = request.simulations().stream().map(FixedIncomeController::toInput).toList();
        List<FixedIncomeSimulation> simulations = service.simulate(inputs);
        var comparison = simulations.size() == 2 ? service.compare(simulations.get(0), simulations.get(1)) : null;
        return FixedIncomeSimulationResponse.of(simulations, comparison);
    }

    private static FixedIncomeInput toInput(FixedIncomeSimulationRequest.Item item) {
        return new FixedIncomeInput(
                item.type(), item.indexer(), item.rate(),
                item.initialCents() == null ? 0 : item.initialCents(),
                item.monthlyContributionCents() == null ? 0 : item.monthlyContributionCents(),
                item.months(), item.startDate(),
                item.cdiAnnualPercent(), item.selicAnnualPercent(), item.ipcaAnnualPercent());
    }
}
