import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "./App";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";

describe("App routes", () => {
  it("renders the dashboard inside the shell at the root route when authenticated", async () => {
    seedAuthenticatedSession();
    renderWithProviders(<App />, { route: "/" });

    // Sidebar nav (shell chrome) and the page content both show up.
    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.getAllByText("Contas").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Cartões").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Categorias").length).toBeGreaterThan(0);
  });

  // FE-2 replaced the transactions placeholder with the real screen.
  it("renders the transactions screen inside the shell", async () => {
    seedAuthenticatedSession();
    renderWithProviders(<App />, { route: "/transactions" });

    expect(await screen.findByRole("heading", { name: "Lançamentos" })).toBeInTheDocument();
  });

  // FE-3 replaced the cards placeholder with the real screen.
  it("renders the cards screen inside the shell", async () => {
    seedAuthenticatedSession();
    renderWithProviders(<App />, { route: "/cards" });

    expect(await screen.findByRole("heading", { name: "Cartões" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Novo cartão" })).toBeInTheDocument();
    expect(
      screen.queryByText("Em construção — esta tela chega nas próximas etapas."),
    ).not.toBeInTheDocument();
  });

  it("redirects unknown routes to the dashboard", async () => {
    seedAuthenticatedSession();
    renderWithProviders(<App />, { route: "/nowhere" });

    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
  });

  it("redirects protected routes to /login when unauthenticated", async () => {
    renderWithProviders(<App />, { route: "/accounts" });

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Entrar" })).toBeInTheDocument();
    });
  });

  it("logs out, clears the session and redirects to /login", async () => {
    const user = userEvent.setup();
    seedAuthenticatedSession();
    renderWithProviders(<App />, { route: "/" });

    await screen.findByRole("heading", { name: "Dashboard" });
    await user.click(screen.getByRole("button", { name: "Sair" }));

    expect(await screen.findByRole("heading", { name: "Entrar" })).toBeInTheDocument();
    expect(localStorage.getItem("mk-finance-mock-session")).toBeNull();
  });
});
