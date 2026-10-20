import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import App from "@/App";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import type {
  FixedIncomeComparison,
  FixedIncomeResult,
  FixedIncomeSimulationRequest,
  MarketRate,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

const marketRates: MarketRate[] = [
  { code: "CDI", ratePercent: 13.65, referenceDate: "2026-10-19", fetchedAt: "2026-10-20T10:00:00Z" },
  { code: "SELIC", ratePercent: 13.75, referenceDate: "2026-10-20", fetchedAt: "2026-10-20T10:00:00Z" },
  { code: "IPCA", ratePercent: 4.58, referenceDate: "2026-09-01", fetchedAt: "2026-10-20T10:00:00Z" },
];

function result(overrides: Partial<FixedIncomeResult> = {}): FixedIncomeResult {
  return {
    type: "CDB",
    indexer: "CDI_PERCENT",
    rate: 110,
    startDate: "2026-10-20",
    endDate: "2027-10-20",
    months: 12,
    assumptions: { cdiAnnualPercent: 13.65 },
    investedCents: 1_000_000,
    grossCents: 1_150_000,
    grossYieldCents: 150_000,
    iofCents: 0,
    incomeTaxCents: 26_250,
    custodyCents: 0,
    netCents: 1_123_750,
    netYieldCents: 123_750,
    netReturnPercent: 12.375,
    netAnnualEquivalentPercent: 12.4,
    series: [
      { month: 0, date: "2026-10-20", investedCents: 1_000_000, grossCents: 1_000_000, netCents: 1_000_000 },
      { month: 12, date: "2027-10-20", investedCents: 1_000_000, grossCents: 1_150_000, netCents: 1_123_750 },
    ],
    ...overrides,
  };
}

function marketHandler(rates: MarketRate[] = marketRates) {
  return http.get("*/market-rates", () => HttpResponse.json(rates));
}

function captureSimulation(sink: FixedIncomeSimulationRequest[], response = result()) {
  return http.post("*/fixed-income/simulations", async ({ request }) => {
    sink.push((await request.json()) as FixedIncomeSimulationRequest);
    return HttpResponse.json({ results: [response] });
  });
}

function captureComparison(
  sink: FixedIncomeSimulationRequest[],
  response: { results: FixedIncomeResult[]; comparison?: FixedIncomeComparison },
) {
  return http.post("*/fixed-income/simulations", async ({ request }) => {
    sink.push((await request.json()) as FixedIncomeSimulationRequest);
    return HttpResponse.json(response);
  });
}

function renderSimulator() {
  seedAuthenticatedSession();
  return renderWithProviders(<App />, { route: "/simulator" });
}

describe("SimulatorPage", () => {
  it("shows the market CDI as the premise hint while the field stays empty", async () => {
    server.use(marketHandler());
    renderSimulator();

    expect(await screen.findByText(/Vazio usa 13,65%/)).toBeInTheDocument();
    expect(screen.getByLabelText("CDI considerado (% ao ano)")).toHaveValue("");
  });

  it("says so when no market rate is available", async () => {
    server.use(marketHandler([]));
    renderSimulator();

    expect(await screen.findByText(/Sem taxa de mercado disponível/)).toBeInTheDocument();
  });

  it("simulates a CDB at 110% of the CDI and shows net, taxes and the equivalent rate", async () => {
    const sent: FixedIncomeSimulationRequest[] = [];
    server.use(marketHandler(), captureSimulation(sent));
    renderSimulator();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("Percentual do CDI (%)"), "110");
    await user.type(screen.getByLabelText("Valor inicial"), "1000000");
    await user.click(screen.getByRole("button", { name: "Simular" }));

    expect(await screen.findByTestId("net-amount")).toHaveTextContent(/11\.237,50/);
    expect(screen.getByText(/Equivale a 12,40% ao ano/)).toBeInTheDocument();
    expect(screen.getByText("Imposto de renda")).toBeInTheDocument();
    expect(screen.getByText(/CDI de 13,65% a\.a\./)).toBeInTheDocument();
    expect(sent).toHaveLength(1);
    expect(sent[0].simulations[0]).toEqual({
      type: "CDB",
      indexer: "CDI_PERCENT",
      rate: 110,
      months: 12,
      initialCents: 1_000_000,
    });
  });

  it("sends the CDI the user typed instead of leaving it for the API", async () => {
    const sent: FixedIncomeSimulationRequest[] = [];
    server.use(marketHandler(), captureSimulation(sent));
    renderSimulator();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("Percentual do CDI (%)"), "100");
    await user.type(screen.getByLabelText("Valor inicial"), "1000000");
    await user.type(screen.getByLabelText("CDI considerado (% ao ano)"), "10,5");
    await user.click(screen.getByRole("button", { name: "Simular" }));

    await waitFor(() => expect(sent).toHaveLength(1));
    expect(sent[0].simulations[0].cdiAnnualPercent).toBe(10.5);
  });

  it("an LCI shows the income tax as exempt", async () => {
    server.use(
      marketHandler(),
      captureSimulation([], result({ type: "LCI", incomeTaxCents: 0, netCents: 1_150_000, netYieldCents: 150_000 })),
    );
    renderSimulator();
    const user = userEvent.setup();

    await user.selectOptions(await screen.findByLabelText("Investimento"), "LCI");
    await user.type(screen.getByLabelText("Percentual do CDI (%)"), "90");
    await user.type(screen.getByLabelText("Valor inicial"), "1000000");
    await user.click(screen.getByRole("button", { name: "Simular" }));

    expect(await screen.findByText("Isento")).toBeInTheDocument();
  });

  it("choosing Tesouro Selic narrows the indexer and asks for the Selic premise", async () => {
    server.use(marketHandler());
    renderSimulator();
    const user = userEvent.setup();

    await user.selectOptions(await screen.findByLabelText("Investimento"), "TESOURO_SELIC");

    expect(screen.getByLabelText("Rentabilidade")).toHaveValue("SELIC_PERCENT");
    expect(screen.getByLabelText("Percentual da Selic (%)")).toBeInTheDocument();
    expect(screen.getByLabelText("Selic considerada (% ao ano)")).toBeInTheDocument();
    expect(screen.queryByLabelText("CDI considerado (% ao ano)")).not.toBeInTheDocument();
  });

  it("blocks the request and points at the fields when the form is incomplete", async () => {
    const sent: FixedIncomeSimulationRequest[] = [];
    server.use(marketHandler(), captureSimulation(sent));
    renderSimulator();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Simular" }));

    expect(await screen.findByText("Informe a taxa.")).toBeInTheDocument();
    expect(screen.getByText(/Informe um valor inicial/)).toBeInTheDocument();
    expect(sent).toHaveLength(0);
  });

  it("explains when the market rate could not be fetched", async () => {
    server.use(
      marketHandler([]),
      http.post("*/fixed-income/simulations", () =>
        HttpResponse.json({ code: "MARKET_RATE_UNAVAILABLE", params: { rate: "CDI" } }, { status: 503 }),
      ),
    );
    renderSimulator();
    const user = userEvent.setup();

    await user.type(await screen.findByLabelText("Percentual do CDI (%)"), "100");
    await user.type(screen.getByLabelText("Valor inicial"), "1000000");
    await user.click(screen.getByRole("button", { name: "Simular" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/Não consegui buscar a taxa de mercado/);
    expect(screen.queryByTestId("net-amount")).not.toBeInTheDocument();
  });

  describe("comparator", () => {
    const lci = result({ type: "LCI", rate: 90, incomeTaxCents: 0, netCents: 1_150_000, netYieldCents: 150_000, netAnnualEquivalentPercent: 14.9 });
    const cdb = result({ netAnnualEquivalentPercent: 12.4 });

    async function openComparator() {
      renderSimulator();
      const user = userEvent.setup();
      await user.click(await screen.findByRole("tab", { name: "Comparar dois" }));
      return user;
    }

    it("opens with the second form set to an LCI that inherits the first one's amounts and term", async () => {
      server.use(marketHandler());
      renderSimulator();
      const user = userEvent.setup();
      await user.type(await screen.findByLabelText("Valor inicial"), "1000000");
      await user.clear(screen.getByLabelText("Prazo (meses)"));
      await user.type(screen.getByLabelText("Prazo (meses)"), "24");

      await user.click(screen.getByRole("tab", { name: "Comparar dois" }));

      const b = screen.getByRole("group", { name: "Simulação B" });
      expect(within(b).getByLabelText("Investimento")).toHaveValue("LCI");
      // Intl puts a non-breaking space after "R$"; compare with it normalised.
      expect((within(b).getByLabelText("Valor inicial") as HTMLInputElement).value.replace(/\s/g, " ")).toBe("R$ 10.000,00");
      expect(within(b).getByLabelText("Prazo (meses)")).toHaveValue("24");
      expect(screen.getByRole("button", { name: "Comparar" })).toBeInTheDocument();
    });

    it("sends both simulations and names the winner with the margin", async () => {
      const sent: FixedIncomeSimulationRequest[] = [];
      server.use(
        marketHandler(),
        captureComparison(sent, {
          results: [cdb, lci],
          comparison: { betterIndex: 1, netDifferenceCents: 26_250, netAnnualEquivalentDifferencePercent: 2.5 },
        }),
      );
      const user = await openComparator();
      const a = screen.getByRole("group", { name: "Simulação A" });
      const b = screen.getByRole("group", { name: "Simulação B" });

      await user.type(within(a).getByLabelText("Valor inicial"), "1000000");
      await user.type(within(b).getByLabelText("Valor inicial"), "1000000");
      await user.type(within(a).getByLabelText("Percentual do CDI (%)"), "110");
      await user.type(within(b).getByLabelText("Percentual do CDI (%)"), "90");
      await user.click(screen.getByRole("button", { name: "Comparar" }));

      const verdict = await screen.findByTestId("comparison-verdict");
      expect(verdict).toHaveTextContent("A simulação B rende mais");
      expect(verdict).toHaveTextContent(/14,90% ao ano, líquido, contra 12,40%/);
      expect(verdict).toHaveTextContent(/262,50 a mais/);
      expect(screen.getAllByTestId("net-amount")).toHaveLength(2);
      expect(sent).toHaveLength(1);
      expect(sent[0].simulations).toHaveLength(2);
      expect(sent[0].simulations[0]).toMatchObject({ type: "CDB", rate: 110, initialCents: 1_000_000 });
      expect(sent[0].simulations[1]).toMatchObject({ type: "LCI", rate: 90, initialCents: 1_000_000 });
    });

    it("says tie when the API gives no winner", async () => {
      server.use(
        marketHandler(),
        captureComparison([], {
          results: [cdb, cdb],
          comparison: { netDifferenceCents: 0, netAnnualEquivalentDifferencePercent: 0 },
        }),
      );
      const user = await openComparator();
      const a = screen.getByRole("group", { name: "Simulação A" });
      const b = screen.getByRole("group", { name: "Simulação B" });

      await user.type(within(a).getByLabelText("Valor inicial"), "1000000");
      await user.type(within(b).getByLabelText("Valor inicial"), "1000000");
      await user.type(within(a).getByLabelText("Percentual do CDI (%)"), "100");
      await user.type(within(b).getByLabelText("Percentual do CDI (%)"), "100");
      await user.click(screen.getByRole("button", { name: "Comparar" }));

      expect(await screen.findByTestId("comparison-verdict")).toHaveTextContent("Empate");
    });

    it("does not send anything while the second form is incomplete, and flags it", async () => {
      const sent: FixedIncomeSimulationRequest[] = [];
      server.use(marketHandler(), captureComparison(sent, { results: [cdb, lci] }));
      const user = await openComparator();
      const a = screen.getByRole("group", { name: "Simulação A" });
      const b = screen.getByRole("group", { name: "Simulação B" });

      await user.type(within(a).getByLabelText("Valor inicial"), "1000000");
      await user.type(within(b).getByLabelText("Valor inicial"), "1000000");
      await user.type(within(a).getByLabelText("Percentual do CDI (%)"), "110");
      await user.click(screen.getByRole("button", { name: "Comparar" }));

      expect(await within(b).findByText("Informe a taxa.")).toBeInTheDocument();
      expect(within(a).queryByText("Informe a taxa.")).not.toBeInTheDocument();
      expect(sent).toHaveLength(0);
    });

    it("going back to a single simulation drops the comparison and sends one item", async () => {
      const sent: FixedIncomeSimulationRequest[] = [];
      server.use(
        marketHandler(),
        captureComparison(sent, {
          results: [cdb, lci],
          comparison: { betterIndex: 1, netDifferenceCents: 1, netAnnualEquivalentDifferencePercent: 1 },
        }),
      );
      const user = await openComparator();
      const a = screen.getByRole("group", { name: "Simulação A" });
      const b = screen.getByRole("group", { name: "Simulação B" });
      await user.type(within(a).getByLabelText("Valor inicial"), "1000000");
      await user.type(within(b).getByLabelText("Valor inicial"), "1000000");
      await user.type(within(a).getByLabelText("Percentual do CDI (%)"), "110");
      await user.type(within(b).getByLabelText("Percentual do CDI (%)"), "90");
      await user.click(screen.getByRole("button", { name: "Comparar" }));
      await screen.findByTestId("comparison-verdict");

      await user.click(screen.getByRole("tab", { name: "Uma simulação" }));

      expect(screen.queryByTestId("comparison-verdict")).not.toBeInTheDocument();
      expect(screen.queryByRole("group", { name: "Simulação B" })).not.toBeInTheDocument();
      expect(screen.getByLabelText("Percentual do CDI (%)")).toHaveValue("110");
    });
  });
});
