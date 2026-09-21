import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import AccountsPage from "@/features/accounts/AccountsPage";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import {
  archivedAccount,
  checkingAccount,
  defaultAccounts,
  financeReadHandlers,
  investmentAccount,
} from "@/test/financeFixtures";
import type { AccountRequest } from "@/lib/api/generated/mKFinanceAPI.schemas";

function renderPage() {
  seedAuthenticatedSession();
  return renderWithProviders(<AccountsPage />, { route: "/accounts" });
}

describe("AccountsPage", () => {
  beforeEach(() => {
    server.use(...financeReadHandlers());
  });

  it("lists accounts with their balances and the workspace totals", async () => {
    renderPage();

    // The mobile list duplicates every row/total alongside the desktop table
    // (README §Mobile — both markups render, CSS picks one per breakpoint),
    // so row content is asserted with getAllByText from here on.
    expect((await screen.findAllByText(checkingAccount.name)).length).toBeGreaterThan(0);
    expect(screen.getAllByText(investmentAccount.name).length).toBeGreaterThan(0);
    // R5 balance of the checking account (row cell + "total without investments").
    expect(screen.getAllByText(/2\.500,00/).length).toBeGreaterThan(0);
    // Total without investments is the checking balance alone; with them, both.
    expect(screen.getAllByText(/7\.500,00/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/2\.300,00/).length).toBeGreaterThan(0);
  });

  it("only lists archived accounts when explicitly asked", async () => {
    server.use(...financeReadHandlers({ accounts: [...defaultAccounts, archivedAccount] }));
    const user = userEvent.setup();
    renderPage();

    await screen.findAllByText(checkingAccount.name);
    expect(screen.queryByText(archivedAccount.name)).not.toBeInTheDocument();

    // Desktop and mobile each render their own "Mostrar arquivadas" toggle —
    // both are bound to the same state, so flipping either one is enough.
    await user.click(screen.getAllByLabelText("Mostrar arquivadas")[0]);

    expect((await screen.findAllByText(archivedAccount.name)).length).toBeGreaterThan(0);
    expect(screen.getAllByText("Arquivada").length).toBeGreaterThan(0);
  });

  it("rejects an account without a name instead of calling the API", async () => {
    const createCalls: AccountRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/accounts", async ({ request }) => {
        createCalls.push((await request.json()) as AccountRequest);
        return HttpResponse.json(checkingAccount, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click((await screen.findAllByRole("button", { name: "Nova conta" }))[0]);
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    expect(await screen.findByText("Campo obrigatório.")).toBeInTheDocument();
    expect(createCalls).toHaveLength(0);
  });

  it("creates an account and sends the balance in cents", async () => {
    const createCalls: AccountRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/accounts", async ({ request }) => {
        createCalls.push((await request.json()) as AccountRequest);
        return HttpResponse.json(checkingAccount, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click((await screen.findAllByRole("button", { name: "Nova conta" }))[0]);
    await user.type(screen.getByLabelText("Nome"), "Carteira");
    await user.selectOptions(screen.getByLabelText("Tipo"), "CASH");
    // Digits are read as cents: "12345" is R$ 123,45.
    await user.type(screen.getByLabelText("Saldo inicial"), "12345");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(createCalls).toHaveLength(1));
    expect(createCalls[0]).toMatchObject({
      name: "Carteira",
      type: "CASH",
      initialBalanceCents: 12345,
      defaultContributionPillar: null,
    });
  });

  it("asks for a default contribution pillar only on investment/savings accounts", async () => {
    const createCalls: AccountRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/accounts", async ({ request }) => {
        createCalls.push((await request.json()) as AccountRequest);
        return HttpResponse.json(investmentAccount, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click((await screen.findAllByRole("button", { name: "Nova conta" }))[0]);
    expect(screen.queryByLabelText("Pilar de aporte padrão")).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Tipo"), "INVESTMENT");
    const pillarSelect = await screen.findByLabelText("Pilar de aporte padrão");
    await user.type(screen.getByLabelText("Nome"), "Tesouro");
    await user.selectOptions(pillarSelect, "GOALS");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(createCalls).toHaveLength(1));
    expect(createCalls[0].defaultContributionPillar).toBe("GOALS");
  });

  it("edits an existing account through the prefilled form", async () => {
    const updateCalls: AccountRequest[] = [];
    server.use(
      http.patch("*/workspaces/:workspaceId/accounts/:accountId", async ({ request }) => {
        updateCalls.push((await request.json()) as AccountRequest);
        return HttpResponse.json(checkingAccount);
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Editar Conta Corrente" }));

    const nameInput = screen.getByLabelText("Nome");
    expect(nameInput).toHaveValue("Conta Corrente");

    await user.clear(nameInput);
    await user.type(nameInput, "Conta Principal");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(updateCalls).toHaveLength(1));
    expect(updateCalls[0].name).toBe("Conta Principal");
  });

  it("archives an account after confirmation instead of deleting it", async () => {
    let archived = false;
    server.use(
      http.post("*/workspaces/:workspaceId/accounts/:accountId/archive", () => {
        archived = true;
        return HttpResponse.json({ ...checkingAccount, archived: true });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Arquivar Conta Corrente" }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/nunca são excluídas/i)).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Arquivar" }));

    await waitFor(() => expect(archived).toBe(true));
  });
});
