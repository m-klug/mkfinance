import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import App from "@/App";
import { renderWithProviders } from "@/test/renderWithProviders";

describe("LoginPage", () => {
  it("submits against the MSW mock and redirects to the dashboard", async () => {
    const user = userEvent.setup();
    renderWithProviders(<App />, { route: "/login" });

    expect(await screen.findByRole("heading", { name: "Entrar" })).toBeInTheDocument();

    await user.type(screen.getByLabelText("E-mail"), "user@example.com");
    await user.type(screen.getByLabelText("Senha"), "password123");
    await user.click(screen.getByRole("button", { name: "Entrar" }));

    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
  });

  it("navigates to the register page from the login page", async () => {
    const user = userEvent.setup();
    renderWithProviders(<App />, { route: "/login" });

    await user.click(await screen.findByRole("link", { name: "Criar conta" }));

    expect(await screen.findByRole("heading", { name: "Criar conta" })).toBeInTheDocument();
  });
});
