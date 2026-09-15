import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import App from "@/App";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import { defaultAccounts, financeReadHandlers, investmentAccount } from "@/test/financeFixtures";
import {
  defaultVaultEntries,
  reserveVault,
  tripVault,
  vaultComposition,
  vaultReadHandlers,
} from "@/test/vaultFixtures";
import type {
  VaultReallocationRequest,
  VaultRequest,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

function renderVaultsPage(accountId = investmentAccount.id) {
  seedAuthenticatedSession();
  return renderWithProviders(<App />, { route: `/accounts/${accountId}/vaults` });
}

describe("VaultsPage", () => {
  beforeEach(() => {
    server.use(
      ...financeReadHandlers({ accounts: defaultAccounts }),
      ...vaultReadHandlers(),
    );
  });

  it("shows the account header and the composition of every vault plus the unallocated slice", async () => {
    renderVaultsPage();

    expect(await screen.findByRole("heading", { name: investmentAccount.name })).toBeInTheDocument();
    // Each vault's name shows up twice: once in the bar legend, once on its own card.
    expect(screen.getAllByText(reserveVault.name).length).toBeGreaterThan(0);
    expect(screen.getAllByText(tripVault.name).length).toBeGreaterThan(0);
    // Reserva's balance, both as a bar legend entry and on its own card.
    expect(screen.getAllByText(/6\.000,00/).length).toBeGreaterThan(0);
    // The R$ 500,00 unallocated slice from the fixture composition. It now
    // shows up twice in the reskinned hero panel: the summary label and the
    // composition bar's legend entry.
    expect(screen.getAllByText("Não alocado").length).toBeGreaterThan(0);
    expect(screen.getAllByText(/500,00/).length).toBeGreaterThan(0);
  });

  it("renders the goal bar for a vault with a target, straight from the API's numbers", async () => {
    renderVaultsPage();

    await screen.findAllByText(tripVault.name);
    // 37.5% rounds to 38% (Math.round) and the API's monthlyNeededCents (625,00).
    expect(screen.getByText("38% da meta")).toBeInTheDocument();
    expect(screen.getByText(/Faltam R\$\s*625,00\/mês/)).toBeInTheDocument();
    expect(screen.getByText("Em andamento")).toBeInTheDocument();
  });

  it("flags a negative unallocated slice instead of hiding or blocking it (§4.3)", async () => {
    server.use(
      ...vaultReadHandlers({
        composition: vaultComposition({ unallocatedCents: -15000, projectedUnallocatedCents: -15000 }),
      }),
    );
    renderVaultsPage();

    await screen.findAllByText(reserveVault.name);
    expect(await screen.findByText(/150,00 a descoberto/)).toBeInTheDocument();
  });

  it("creates a vault with an optional target amount and no deadline when left blank", async () => {
    const createCalls: VaultRequest[] = [];
    server.use(
      http.post(
        "*/workspaces/:workspaceId/accounts/:accountId/vaults",
        async ({ request }) => {
          createCalls.push((await request.json()) as VaultRequest);
          return HttpResponse.json({ ...reserveVault, id: "vault-new" }, { status: 201 });
        },
      ),
    );
    const user = userEvent.setup();
    renderVaultsPage();

    await user.click(await screen.findByRole("button", { name: "Nova caixinha" }));
    await user.type(screen.getByLabelText("Nome"), "Casa Nova");
    await user.selectOptions(screen.getByLabelText("Pilar"), "GOALS");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(createCalls).toHaveLength(1));
    expect(createCalls[0]).toMatchObject({
      name: "Casa Nova",
      contributionPillar: "GOALS",
      targetAmountCents: null,
      targetDate: null,
    });
  });

  it("only offers the deadline field once a target amount is set", async () => {
    const user = userEvent.setup();
    renderVaultsPage();

    await user.click(await screen.findByRole("button", { name: "Nova caixinha" }));
    expect(screen.queryByLabelText("Prazo")).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Valor-alvo"), "1000000");
    expect(await screen.findByLabelText("Prazo")).toBeInTheDocument();
  });

  it("edits an existing vault through the prefilled form", async () => {
    const updateCalls: VaultRequest[] = [];
    server.use(
      http.patch("*/workspaces/:workspaceId/vaults/:vaultId", async ({ request }) => {
        updateCalls.push((await request.json()) as VaultRequest);
        return HttpResponse.json(reserveVault);
      }),
    );
    const user = userEvent.setup();
    renderVaultsPage();

    await user.click(await screen.findByRole("button", { name: `Editar ${reserveVault.name}` }));
    const nameInput = screen.getByLabelText("Nome");
    expect(nameInput).toHaveValue(reserveVault.name);

    await user.clear(nameInput);
    await user.type(nameInput, "Reserva de Emergência");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(updateCalls).toHaveLength(1));
    expect(updateCalls[0].name).toBe("Reserva de Emergência");
  });

  it("archives a vault after confirmation", async () => {
    let archived = false;
    server.use(
      http.post("*/workspaces/:workspaceId/vaults/:vaultId/archive", () => {
        archived = true;
        return HttpResponse.json({ ...reserveVault, archived: true });
      }),
    );
    const user = userEvent.setup();
    renderVaultsPage();

    await user.click(await screen.findByRole("button", { name: `Arquivar ${reserveVault.name}` }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Arquivar" }));

    await waitFor(() => expect(archived).toBe(true));
  });

  it("deletes a vault after confirmation", async () => {
    let deleted = false;
    server.use(
      http.delete("*/workspaces/:workspaceId/vaults/:vaultId", () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderVaultsPage();

    await user.click(await screen.findByRole("button", { name: `Excluir ${tripVault.name}` }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Excluir" }));

    await waitFor(() => expect(deleted).toBe(true));
  });

  it("reallocates pre-existing account balance into a vault via the null 'Não alocado' option (§4.7)", async () => {
    const reallocateCalls: VaultReallocationRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/vaults/reallocations", async ({ request }) => {
        reallocateCalls.push((await request.json()) as VaultReallocationRequest);
        return HttpResponse.json({
          id: "realloc-x",
          accountId: investmentAccount.id,
          fromVaultId: null,
          toVaultId: reserveVault.id,
          amountCents: 20000,
          date: "2026-08-01",
        });
      }),
    );
    const user = userEvent.setup();
    renderVaultsPage();

    await user.click(await screen.findByRole("button", { name: "Remanejar" }));
    const dialog = await screen.findByRole("dialog");
    // "De" stays on the literal "Não alocado" (value=""); only "Para" is set.
    await user.selectOptions(within(dialog).getByLabelText("Para"), reserveVault.id);
    await user.type(within(dialog).getByLabelText("Valor"), "20000");
    await user.click(within(dialog).getByRole("button", { name: "Remanejar" }));

    await waitFor(() => expect(reallocateCalls).toHaveLength(1));
    expect(reallocateCalls[0]).toMatchObject({
      fromVaultId: null,
      toVaultId: reserveVault.id,
      amountCents: 20000,
    });
  });

  it("blocks a reallocation left with both sides unallocated instead of calling the API", async () => {
    const reallocateCalls: VaultReallocationRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/vaults/reallocations", async ({ request }) => {
        reallocateCalls.push((await request.json()) as VaultReallocationRequest);
        return HttpResponse.json({});
      }),
    );
    const user = userEvent.setup();
    renderVaultsPage();

    await user.click(await screen.findByRole("button", { name: "Remanejar" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("Valor"), "100");
    await user.click(within(dialog).getByRole("button", { name: "Remanejar" }));

    expect(
      await screen.findByText("Selecione ao menos uma caixinha de origem ou destino."),
    ).toBeInTheDocument();
    expect(reallocateCalls).toHaveLength(0);
  });

  it("shows the vault's own signed ledger in the entries sheet", async () => {
    const user = userEvent.setup();
    renderVaultsPage();

    await user.click(
      await screen.findByRole("button", { name: `Ver extrato de ${reserveVault.name}` }),
    );

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Extrato de Reserva")).toBeInTheDocument();
    expect(within(dialog).getByText("Aporte")).toBeInTheDocument();
    expect(within(dialog).getByText("Remanejo")).toBeInTheDocument();
    // Only Reserva's own two entries — Viagem's would not show up here.
    expect(defaultVaultEntries.every((entry) => entry.vaultId === reserveVault.id)).toBe(true);
  });
});

describe("AccountsPage → Caixinhas entry point", () => {
  beforeEach(() => {
    server.use(...financeReadHandlers({ accounts: defaultAccounts }), ...vaultReadHandlers());
  });

  it("only offers the Caixinhas action on INVESTMENT/SAVINGS accounts", async () => {
    seedAuthenticatedSession();
    renderWithProviders(<App />, { route: "/accounts" });

    expect(await screen.findByText(investmentAccount.name)).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: `Caixinhas de ${investmentAccount.name}` }),
    ).toBeInTheDocument();
    // Checking is neither INVESTMENT nor SAVINGS.
    expect(
      screen.queryByRole("link", { name: /Caixinhas de Conta Corrente/ }),
    ).not.toBeInTheDocument();
  });

  it("navigates to the vaults screen from the accounts list", async () => {
    const user = userEvent.setup();
    seedAuthenticatedSession();
    renderWithProviders(<App />, { route: "/accounts" });

    await user.click(
      await screen.findByRole("link", { name: `Caixinhas de ${investmentAccount.name}` }),
    );

    expect(await screen.findByRole("heading", { name: investmentAccount.name })).toBeInTheDocument();
    expect(screen.getAllByText(reserveVault.name).length).toBeGreaterThan(0);
  });
});
