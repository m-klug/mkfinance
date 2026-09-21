import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import App from "@/App";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import { defaultAccounts, financeReadHandlers, investmentAccount } from "@/test/financeFixtures";
import { reserveVault, tripVault, vaultReadHandlers } from "@/test/vaultFixtures";
import type { VaultYield, VaultYieldRequest } from "@/lib/api/generated/mKFinanceAPI.schemas";

function renderVaultsPage() {
  seedAuthenticatedSession();
  return renderWithProviders(<App />, { route: `/accounts/${investmentAccount.id}/vaults` });
}

function captureYield(sink: VaultYieldRequest[], response: Partial<VaultYield> = {}) {
  return http.post(
    "*/workspaces/:workspaceId/accounts/:accountId/vault-yields",
    async ({ request }) => {
      sink.push((await request.json()) as VaultYieldRequest);
      return HttpResponse.json({
        transactionId: "tx-yield",
        amountCents: 10001,
        date: "2026-08-01",
        shares: [
          { vaultId: reserveVault.id, amountCents: 5715 },
          { vaultId: tripVault.id, amountCents: 4286 },
        ],
        ...response,
      });
    },
  );
}

describe("VaultYieldDialog", () => {
  beforeEach(() => {
    server.use(
      ...financeReadHandlers({ accounts: defaultAccounts }),
      ...vaultReadHandlers(),
    );
  });

  it("opens from the vaults screen and previews the pro-rata split before confirming", async () => {
    const user = userEvent.setup();
    renderVaultsPage();

    await user.click((await screen.findAllByRole("button", { name: "Lançar rendimento" }))[0]);
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByRole("heading", { name: "Lançar rendimento" })).toBeInTheDocument();

    // Canonical §4.5 scenario: bases 6.000/4.500, rendimento de 100,01 → 57,15 + 42,86.
    await user.type(within(dialog).getByLabelText("Valor do rendimento"), "10001");

    expect(within(dialog).getByText("Prévia do rateio")).toBeInTheDocument();
    expect(within(dialog).getByText(reserveVault.name)).toBeInTheDocument();
    expect(within(dialog).getByText("R$ 57,15")).toBeInTheDocument();
    expect(within(dialog).getByText(tripVault.name)).toBeInTheDocument();
    expect(within(dialog).getByText("R$ 42,86")).toBeInTheDocument();
    // The preview is explicit about not being the final word.
    expect(within(dialog).getByText(/prévia pode variar/)).toBeInTheDocument();
  });

  it("submits amount, date and description, and reports the real split from the server on success", async () => {
    const calls: VaultYieldRequest[] = [];
    server.use(captureYield(calls));
    const user = userEvent.setup();
    renderVaultsPage();

    await user.click((await screen.findAllByRole("button", { name: "Lançar rendimento" }))[0]);
    const dialog = await screen.findByRole("dialog");

    await user.type(within(dialog).getByLabelText("Valor do rendimento"), "10001");
    await user.clear(within(dialog).getByLabelText("Data"));
    await user.type(within(dialog).getByLabelText("Data"), "2026-08-01");
    await user.type(within(dialog).getByLabelText("Descrição"), "Rendimento de julho");
    await user.click(within(dialog).getByRole("button", { name: "Lançar rendimento" }));

    await waitFor(() => expect(calls).toHaveLength(1));
    expect(calls[0]).toMatchObject({
      amountCents: 10001,
      date: "2026-08-01",
      description: "Rendimento de julho",
    });
    expect(await screen.findByText("Rendimento lançado.")).toBeInTheDocument();
  });

  it("blocks submission with a zero amount", async () => {
    const calls: VaultYieldRequest[] = [];
    server.use(captureYield(calls));
    const user = userEvent.setup();
    renderVaultsPage();

    await user.click((await screen.findAllByRole("button", { name: "Lançar rendimento" }))[0]);
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Lançar rendimento" }));

    expect(
      await within(dialog).findByText("Informe um valor maior que zero."),
    ).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it("says the whole amount falls into the unallocated slice when no vault has a positive balance", async () => {
    server.use(
      ...vaultReadHandlers({
        composition: {
          accountId: investmentAccount.id,
          balanceCents: 0,
          projectedBalanceCents: 0,
          allocatedCents: 0,
          unallocatedCents: 0,
          projectedUnallocatedCents: 0,
          vaults: [{ ...reserveVault, balanceCents: 0 }, { ...tripVault, balanceCents: 0, goal: undefined }],
        },
      }),
    );
    const user = userEvent.setup();
    renderVaultsPage();

    await user.click((await screen.findAllByRole("button", { name: "Lançar rendimento" }))[0]);
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText("Valor do rendimento"), "5000");

    expect(
      within(dialog).getByText(/Nenhuma caixinha tem saldo positivo/),
    ).toBeInTheDocument();
  });
});
