import { screen } from "@testing-library/react";

import DashboardPage from "@/features/dashboard/DashboardPage";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import {
  dashboardHandlers,
  noIncomeAllocation,
  normalAllocation,
  overrunAllocation,
} from "@/features/dashboard/dashboardFixtures";

function renderPage() {
  seedAuthenticatedSession();
  return renderWithProviders(<DashboardPage />, { route: "/" });
}

describe("DashboardPage", () => {
  it("renders the normal state: allocation bars, balances and breakdown, no overrun", async () => {
    server.use(...dashboardHandlers({ allocation: normalAllocation }));
    renderPage();

    // Pillar allocation section with every pillar labelled (also appears in the
    // category filter chips, hence getAllByText for the shared ones).
    expect(await screen.findByText("Alocação por pilar")).toBeInTheDocument();
    expect(screen.getAllByText("Custos Fixos").length).toBeGreaterThan(0);
    expect(screen.getByText("Liberdade Financeira")).toBeInTheDocument();

    // Balances: total without and with investments.
    expect(screen.getByText(/3\.210,00/)).toBeInTheDocument();
    expect(screen.getByText(/9\.870,00/)).toBeInTheDocument();

    // Month income (R$ 10.000,00) and category breakdown names.
    expect(screen.getByText(/10\.000,00/)).toBeInTheDocument();
    expect(screen.getByText("Mercado")).toBeInTheDocument();
    expect(screen.getByText("Streaming")).toBeInTheDocument();

    // Open statement resolves its card name.
    expect(screen.getByText("Cartão Nubank")).toBeInTheDocument();

    // Nothing is over target in this scenario.
    expect(screen.queryByText("Estourou")).not.toBeInTheDocument();
    expect(screen.queryByText(/acima do alvo/i)).not.toBeInTheDocument();
  });

  it("flags the overrun state when a pillar is above its target", async () => {
    server.use(...dashboardHandlers({ allocation: overrunAllocation }));
    renderPage();

    expect(await screen.findByText("Alocação por pilar")).toBeInTheDocument();
    // Warning banner plus the per-pillar badge.
    expect(screen.getByText(/acima do alvo/i)).toBeInTheDocument();
    expect(screen.getByText("Estourou")).toBeInTheDocument();
  });

  it("handles the no-income state with absolute values and a warning, never a percent", async () => {
    server.use(...dashboardHandlers({ allocation: noIncomeAllocation }));
    renderPage();

    expect(
      await screen.findByText(/Sem receita registrada neste mês/i),
    ).toBeInTheDocument();

    // Percent meters are not rendered when there is no income (no divide-by-zero).
    expect(screen.queryByRole("meter")).not.toBeInTheDocument();

    // Absolute spent value for a pillar is still shown (Custos Fixos: R$ 2.500,00).
    expect(screen.getAllByText(/2\.500,00/).length).toBeGreaterThan(0);
  });
});
