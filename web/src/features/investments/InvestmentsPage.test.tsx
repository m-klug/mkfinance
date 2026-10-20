import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import App from "@/App";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import { defaultAccounts, financeReadHandlers, investmentAccount } from "@/test/financeFixtures";
import { reserveVault, vaultReadHandlers } from "@/test/vaultFixtures";
import { todayIso } from "@/features/transactions/transactionUtils";
import type {
  InvestmentSummary,
  Position,
  PositionRequest,
  PositionSettleRequest,
  PositionUpdateRequest,
  UnlinkedContribution,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

const valuation = (netCents: number, asOf: string) => ({
  asOf,
  grossCents: netCents + 10_000,
  grossYieldCents: netCents + 10_000 - 500_000,
  iofCents: 0,
  incomeTaxCents: 10_000,
  custodyCents: 0,
  netCents,
  netYieldCents: netCents - 500_000,
  lastIndexDate: "2026-10-19",
});

const linkedPosition: Position = {
  id: "pos-linked",
  origin: "CONTRIBUTION",
  contributionTransactionId: "leg-1",
  accountId: investmentAccount.id,
  accountName: "Corretora",
  vaultId: reserveVault.id,
  vaultName: "Reserva",
  name: "CDB Inter jan/27",
  type: "CDB",
  indexer: "CDI_PERCENT",
  rate: 110,
  principalCents: 500_000,
  purchaseDate: "2026-07-10",
  maturityDate: "2027-07-10",
  status: "ACTIVE",
  awaitingPayment: false,
  estimate: { today: valuation(523_450, todayIso()), atMaturity: valuation(556_000, "2027-07-10") },
};

const manualPosition: Position = {
  id: "pos-manual",
  origin: "MANUAL",
  accountId: investmentAccount.id,
  accountName: "Corretora",
  vaultId: null,
  name: null,
  type: "LCI",
  indexer: "PREFIXED",
  rate: 12,
  principalCents: 300_000,
  purchaseDate: "2026-03-10",
  maturityDate: "2026-10-01",
  status: "ACTIVE",
  awaitingPayment: true,
  estimate: { today: valuation(318_000, "2026-10-01"), atMaturity: valuation(318_000, "2026-10-01") },
};

const settledPosition: Position = {
  ...linkedPosition,
  id: "pos-settled",
  name: "LCA antiga",
  status: "SETTLED",
  estimate: null,
  settlement: { settledDate: "2026-09-01", receivedCents: 540_000, estimatedNetCents: 538_000, yieldCents: 40_000 },
};

const contribution: UnlinkedContribution = {
  transactionId: "leg-9",
  accountId: investmentAccount.id,
  accountName: "Corretora",
  vaultId: reserveVault.id,
  vaultName: "Reserva",
  amountCents: 250_000,
  date: "2026-09-15",
  description: "Compra CDB",
};

const healthySummary: InvestmentSummary = {
  accountsAndCashCents: 520_000,
  investmentsCents: 868_450,
  estimatedNetWorthCents: 1_388_450,
  openStatementsCents: 150_000,
  assetClasses: [
    {
      code: "FIXED_INCOME",
      appliedCents: 800_000,
      estimatedValueCents: 818_450,
      accruedYieldCents: 18_450,
      positions: 2,
      positionsWithoutEstimate: 0,
    },
  ],
  accounts: [
    {
      accountId: "acc-investment",
      name: "Corretora",
      balanceCents: 850_000,
      positionsPrincipalCents: 800_000,
      accruedYieldCents: 18_450,
      unregisteredCents: 50_000,
      positionsExceedBalance: false,
    },
  ],
  unregisteredInvestmentCents: 50_000,
  positionsExceedBalance: false,
};

const exceedingSummary: InvestmentSummary = {
  ...healthySummary,
  openStatementsCents: 0,
  accounts: [
    {
      accountId: "acc-investment",
      name: "Corretora",
      balanceCents: 0,
      positionsPrincipalCents: 400_000,
      accruedYieldCents: 9_000,
      unregisteredCents: 0,
      positionsExceedBalance: true,
    },
  ],
  unregisteredInvestmentCents: 0,
  positionsExceedBalance: true,
};

interface Sinks {
  created: PositionRequest[];
  updated: { id: string; body: PositionUpdateRequest }[];
  settled: { id: string; body: PositionSettleRequest }[];
  unsettled: string[];
  deleted: string[];
}

function handlers(
  positions: Position[],
  contributions: UnlinkedContribution[] = [],
  summary: InvestmentSummary = healthySummary,
): Sinks {
  const sinks: Sinks = { created: [], updated: [], settled: [], unsettled: [], deleted: [] };
  server.use(
    ...financeReadHandlers({ accounts: defaultAccounts }),
    ...vaultReadHandlers(),
    http.get("*/workspaces/:workspaceId/investments/positions", ({ request }) => {
      const status = new URL(request.url).searchParams.get("status");
      return HttpResponse.json(status ? positions.filter((p) => p.status === status) : positions);
    }),
    http.get("*/workspaces/:workspaceId/investments/unlinked-contributions", () => HttpResponse.json(contributions)),
    http.get("*/workspaces/:workspaceId/investments/summary", () => HttpResponse.json(summary)),
    http.post("*/workspaces/:workspaceId/investments/positions", async ({ request }) => {
      sinks.created.push((await request.json()) as PositionRequest);
      return HttpResponse.json(linkedPosition, { status: 201 });
    }),
    http.put("*/workspaces/:workspaceId/investments/positions/:id", async ({ request, params }) => {
      sinks.updated.push({ id: String(params.id), body: (await request.json()) as PositionUpdateRequest });
      return HttpResponse.json(linkedPosition);
    }),
    http.post("*/workspaces/:workspaceId/investments/positions/:id/settle", async ({ request, params }) => {
      sinks.settled.push({ id: String(params.id), body: (await request.json()) as PositionSettleRequest });
      return HttpResponse.json(settledPosition);
    }),
    http.post("*/workspaces/:workspaceId/investments/positions/:id/unsettle", ({ params }) => {
      sinks.unsettled.push(String(params.id));
      return HttpResponse.json(linkedPosition);
    }),
    http.delete("*/workspaces/:workspaceId/investments/positions/:id", ({ params }) => {
      sinks.deleted.push(String(params.id));
      return new HttpResponse(null, { status: 204 });
    }),
    http.get("*/workspaces/:workspaceId/transactions", () =>
      HttpResponse.json({ content: [], page: 0, size: 100, totalElements: 0 }),
    ),
  );
  return sinks;
}

function renderPage() {
  seedAuthenticatedSession();
  return renderWithProviders(<App />, { route: "/investments" });
}

async function openMenuOf(card: HTMLElement, user: ReturnType<typeof userEvent.setup>) {
  await user.click(within(card).getByRole("button", { name: "Ações" }));
}

describe("InvestmentsPage", () => {
  describe("listing", () => {
    it("lists the active positions with their estimates, flags the one due and the one from before the app", async () => {
      handlers([linkedPosition, manualPosition]);
      renderPage();

      const cards = await screen.findAllByTestId("position-card");
      expect(cards).toHaveLength(2);

      const linked = cards[0];
      expect(within(linked).getByText("CDB Inter jan/27")).toBeInTheDocument();
      expect(within(linked).getByText(/110,00% do CDI/)).toBeInTheDocument();
      expect(within(linked).getByTestId("value-today")).toHaveTextContent(/5\.234,50/);
      expect(within(linked).getByText(/Vence em/)).toBeInTheDocument();
      expect(within(linked).getByText(/Índice até/)).toBeInTheDocument();

      const due = cards[1];
      expect(within(due).getByText("Aguardando pagamento")).toBeInTheDocument();
      expect(within(due).getByText("Anterior ao app")).toBeInTheDocument();
      expect(within(due).getByText(/Venceu em/)).toBeInTheDocument();
    });

    it("shows paid positions under their own tab, with received next to estimated", async () => {
      handlers([linkedPosition, settledPosition]);
      renderPage();
      const user = userEvent.setup();

      await screen.findAllByTestId("position-card");
      await user.click(screen.getByRole("tab", { name: "Pagas" }));

      const card = await screen.findByText("LCA antiga");
      const paid = card.closest("[data-testid=position-card]") as HTMLElement;
      expect(within(paid).getByText("Paga")).toBeInTheDocument();
      expect(within(paid).getByTestId("received")).toHaveTextContent(/5\.400,00/);
      expect(within(paid).getByText("Estimado")).toBeInTheDocument();
      expect(within(paid).getByText(/Paga em/)).toBeInTheDocument();
    });

    it("says so when there is nothing yet", async () => {
      handlers([]);
      renderPage();

      expect(await screen.findByText("Nenhuma posição ativa.")).toBeInTheDocument();
    });
  });

  describe("registering a title", () => {
    it("from a purchase: takes the purchase's vault and sends the purchase, not the manual fields", async () => {
      const sinks = handlers([], [contribution]);
      renderPage();
      const user = userEvent.setup();

      await user.click(await screen.findByRole("button", { name: "Nova posição" }));
      await user.selectOptions(await screen.findByLabelText("Aporte (compra)"), "leg-9");
      await user.type(screen.getByLabelText("Percentual do CDI (%)"), "110");
      fireEvent.change(screen.getByLabelText("Vencimento"), { target: { value: "2027-09-15" } });
      await waitFor(() => expect(screen.getByLabelText("Caixinha do rendimento")).toHaveValue("vault-reserve"));
      await user.click(screen.getByRole("button", { name: "Salvar" }));

      await waitFor(() => expect(sinks.created).toHaveLength(1));
      expect(sinks.created[0]).toEqual({
        type: "CDB",
        indexer: "CDI_PERCENT",
        rate: 110,
        maturityDate: "2027-09-15",
        vaultId: "vault-reserve",
        contributionTransactionId: "leg-9",
      });
    });

    it("from before the app: sends account, amount and purchase date, and warns about the initial balance", async () => {
      const sinks = handlers([]);
      renderPage();
      const user = userEvent.setup();

      await user.click(await screen.findByRole("button", { name: "Nova posição" }));
      await user.click(screen.getByRole("tab", { name: "Título anterior ao app" }));
      expect(screen.getByText(/some o valor aplicado ao saldo inicial/)).toBeInTheDocument();

      await user.selectOptions(await screen.findByLabelText("Conta de investimento"), investmentAccount.id);
      await user.type(screen.getByLabelText("Valor aplicado"), "800000");
      fireEvent.change(screen.getByLabelText("Data da compra"), { target: { value: "2026-03-10" } });
      await user.selectOptions(screen.getByLabelText("Investimento"), "LCI");
      await user.selectOptions(screen.getByLabelText("Rentabilidade"), "PREFIXED");
      await user.type(screen.getByLabelText("Taxa (% ao ano)"), "12,5");
      fireEvent.change(screen.getByLabelText("Vencimento"), { target: { value: "2027-03-10" } });
      await user.click(screen.getByRole("button", { name: "Salvar" }));

      await waitFor(() => expect(sinks.created).toHaveLength(1));
      expect(sinks.created[0]).toEqual({
        type: "LCI",
        indexer: "PREFIXED",
        rate: 12.5,
        maturityDate: "2027-03-10",
        accountId: investmentAccount.id,
        principalCents: 800_000,
        purchaseDate: "2026-03-10",
      });
    });

    it("does not send anything while the form is incomplete, and points at the fields", async () => {
      const sinks = handlers([], [contribution]);
      renderPage();
      const user = userEvent.setup();

      await user.click(await screen.findByRole("button", { name: "Nova posição" }));
      await user.click(await screen.findByRole("button", { name: "Salvar" }));

      expect(await screen.findByText("Escolha o aporte.")).toBeInTheDocument();
      expect(screen.getByText("Informe a taxa.")).toBeInTheDocument();
      expect(screen.getByText("Informe o vencimento.")).toBeInTheDocument();
      expect(sinks.created).toHaveLength(0);
    });

    it("offers no IPCA-linked title or indexer", async () => {
      handlers([]);
      renderPage();
      const user = userEvent.setup();

      await user.click(await screen.findByRole("button", { name: "Nova posição" }));
      const type = await screen.findByLabelText("Investimento");

      expect(within(type).queryByRole("option", { name: "Tesouro IPCA+" })).not.toBeInTheDocument();
      expect(within(screen.getByLabelText("Rentabilidade")).queryByRole("option", { name: "IPCA + taxa fixa" })).not.toBeInTheDocument();
    });
  });

  describe("editing and deleting", () => {
    it("edits an active position, replacing its terms", async () => {
      const sinks = handlers([linkedPosition]);
      renderPage();
      const user = userEvent.setup();

      const [card] = await screen.findAllByTestId("position-card");
      await openMenuOf(card, user);
      await user.click(await screen.findByRole("menuitem", { name: "Editar" }));
      const rate = await screen.findByLabelText("Percentual do CDI (%)");
      expect(rate).toHaveValue("110");
      await user.clear(rate);
      await user.type(rate, "115");
      await user.click(screen.getByRole("button", { name: "Salvar" }));

      await waitFor(() => expect(sinks.updated).toHaveLength(1));
      expect(sinks.updated[0].id).toBe("pos-linked");
      expect(sinks.updated[0].body).toMatchObject({ rate: 115, maturityDate: "2027-07-10", vaultId: "vault-reserve" });
      expect(sinks.updated[0].body.principalCents).toBeUndefined(); // a linked position keeps its purchase's amount
    });

    it("deletes after confirming", async () => {
      const sinks = handlers([linkedPosition]);
      renderPage();
      const user = userEvent.setup();

      const [card] = await screen.findAllByTestId("position-card");
      await openMenuOf(card, user);
      await user.click(await screen.findByRole("menuitem", { name: "Excluir" }));
      expect(await screen.findByText("Excluir posição?")).toBeInTheDocument();
      await user.click(screen.getAllByRole("button", { name: "Excluir" }).at(-1)!);

      await waitFor(() => expect(sinks.deleted).toEqual(["pos-linked"]));
    });
  });

  describe("paying a title", () => {
    it("starts from the estimate, shows the yield that will be booked and sends date and amount", async () => {
      const sinks = handlers([linkedPosition]);
      renderPage();
      const user = userEvent.setup();

      const [card] = await screen.findAllByTestId("position-card");
      await openMenuOf(card, user);
      await user.click(await screen.findByRole("menuitem", { name: "Baixar título" }));

      const received = await screen.findByLabelText("Valor líquido recebido");
      expect((received as HTMLInputElement).value.replace(/\s/g, " ")).toBe("R$ 5.560,00"); // estimate at maturity
      expect(screen.getByText(/Estimativa do app para o vencimento/)).toBeInTheDocument();
      expect(screen.getByText(/O rendimento vai para a caixinha Reserva/)).toBeInTheDocument();

      await user.clear(received);
      await user.type(received, "540000");
      expect(screen.getByTestId("settle-yield")).toHaveTextContent(/400,00/);
      await user.click(screen.getByRole("button", { name: "Baixar" }));

      await waitFor(() => expect(sinks.settled).toHaveLength(1));
      expect(sinks.settled[0]).toEqual({ id: "pos-linked", body: { settledDate: todayIso(), receivedCents: 540_000 } });
    });

    it("warns when the title has no caixinha: the yield stays unallocated and the way back needs a remap", async () => {
      handlers([manualPosition]);
      renderPage();
      const user = userEvent.setup();

      const [card] = await screen.findAllByTestId("position-card");
      await openMenuOf(card, user);
      await user.click(await screen.findByRole("menuitem", { name: "Baixar título" }));

      expect(await screen.findByText(/O rendimento fica na parte não alocada/)).toBeInTheDocument();
      expect(screen.getByText(/Este título não tem caixinha/)).toBeInTheDocument();
    });

    it("refuses an amount below the principal without calling the API", async () => {
      const sinks = handlers([linkedPosition]);
      renderPage();
      const user = userEvent.setup();

      const [card] = await screen.findAllByTestId("position-card");
      await openMenuOf(card, user);
      await user.click(await screen.findByRole("menuitem", { name: "Baixar título" }));
      const received = await screen.findByLabelText("Valor líquido recebido");
      await user.clear(received);
      await user.type(received, "100000");
      await user.click(screen.getByRole("button", { name: "Baixar" }));

      expect(await screen.findByText(/não pode ser menor que o aplicado/)).toBeInTheDocument();
      expect(sinks.settled).toHaveLength(0);
    });

    it("explains the API's refusal in plain words", async () => {
      const sinks = handlers([linkedPosition]);
      server.use(
        http.post("*/workspaces/:workspaceId/investments/positions/:id/settle", () =>
          HttpResponse.json({ code: "POSITION_DATES_INVALID" }, { status: 400 }),
        ),
      );
      renderPage();
      const user = userEvent.setup();

      const [card] = await screen.findAllByTestId("position-card");
      await openMenuOf(card, user);
      await user.click(await screen.findByRole("menuitem", { name: "Baixar título" }));
      await user.click(await screen.findByRole("button", { name: "Baixar" }));

      expect(await screen.findByText(/Confira as datas/)).toBeInTheDocument();
      expect(sinks.settled).toHaveLength(0);
    });
  });

  describe("undoing a payment", () => {
    it("asks first, warning that the yield entry is deleted, then undoes it", async () => {
      const sinks = handlers([settledPosition]);
      renderPage();
      const user = userEvent.setup();

      await user.click(await screen.findByRole("tab", { name: "Pagas" }));
      const [card] = await screen.findAllByTestId("position-card");
      await openMenuOf(card, user);
      await user.click(await screen.findByRole("menuitem", { name: "Desfazer baixa" }));
      expect(await screen.findByText(/Apaga o lançamento de rendimento/)).toBeInTheDocument();
      await user.click(screen.getAllByRole("button", { name: "Desfazer baixa" }).at(-1)!);

      await waitFor(() => expect(sinks.unsettled).toEqual(["pos-settled"]));
    });
  });

  describe("the net-worth summary", () => {
    it("shows the estimated net worth, the groups, and what the active positions have accrued", async () => {
      handlers([linkedPosition]);
      renderPage();

      expect(await screen.findByTestId("net-worth")).toHaveTextContent(/13\.884,50/);
      expect(screen.getByTestId("accounts-and-cash")).toHaveTextContent(/5\.200,00/);
      expect(screen.getByTestId("investments-total")).toHaveTextContent(/8\.684,50/);
      expect(screen.getByText(/Inclui .*184,50.* de rendimento já acumulado e ainda não recebido/)).toBeInTheDocument();
    });

    it("shows the fixed-income class and, per account, the part no position explains", async () => {
      handlers([linkedPosition]);
      renderPage();

      const section = await screen.findByRole("region", { name: "Patrimônio" });
      expect(within(section).getByText("Renda fixa")).toBeInTheDocument();
      expect(within(section).getByText("2 posições")).toBeInTheDocument();
      const account = within(section).getByTestId("summary-account");
      expect(within(account).getByText("Corretora")).toBeInTheDocument();
      const unregistered = within(account).getByText("Sem posição cadastrada").parentElement as HTMLElement;
      expect(unregistered).toHaveTextContent(/500,00/); // the 50.000 cents nothing explains
    });

    it("shows the card bills still owed as information, not as a deduction", async () => {
      handlers([linkedPosition]);
      renderPage();

      expect(await screen.findByTestId("open-statements")).toHaveTextContent(/1\.500,00.*Não descontadas/);
    });

    it("raises no warning when the positions fit in the accounts", async () => {
      handlers([linkedPosition]);
      renderPage();

      await screen.findByTestId("net-worth");
      expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("warns when the positions claim more than the account holds, naming it and the way out", async () => {
      handlers([linkedPosition], [], exceedingSummary);
      renderPage();

      const alert = await screen.findByRole("alert");
      expect(alert).toHaveTextContent("As posições somam mais que o saldo desta conta. (Corretora)");
      expect(alert).toHaveTextContent(/some o valor aplicado ao saldo inicial/);
      expect(within(alert).getByRole("link", { name: "Abrir contas" })).toHaveAttribute("href", "/accounts");
      expect(screen.queryByTestId("open-statements")).not.toBeInTheDocument(); // nothing owed
    });

    it("hides the part nothing explains when there is none, and the class when there are no positions", async () => {
      handlers([], [], {
        ...healthySummary,
        assetClasses: [{ code: "FIXED_INCOME", appliedCents: 0, estimatedValueCents: 0, accruedYieldCents: 0, positions: 0, positionsWithoutEstimate: 0 }],
        accounts: [{ ...healthySummary.accounts[0], unregisteredCents: 0 }],
        estimatedNetWorthCents: 1_370_000,
      });
      renderPage();

      await screen.findByTestId("net-worth");
      expect(screen.queryByText("Renda fixa")).not.toBeInTheDocument();
      expect(screen.queryByText("Sem posição cadastrada")).not.toBeInTheDocument();
      expect(screen.queryByText(/Inclui/)).not.toBeInTheDocument(); // no accrued yield to mention
    });

    it("still shows the positions when the summary cannot be read", async () => {
      handlers([linkedPosition]);
      server.use(
        http.get("*/workspaces/:workspaceId/investments/summary", () => HttpResponse.json({ code: "INTERNAL_ERROR" }, { status: 500 })),
      );
      renderPage();

      expect(await screen.findAllByTestId("position-card")).toHaveLength(1);
      expect(screen.queryByTestId("net-worth")).not.toBeInTheDocument();
    });
  });

  describe("the Aplicações section of the caixinhas", () => {
    it("lists the account's active titles under its caixinhas, with a link to the investments page", async () => {
      handlers([linkedPosition, { ...manualPosition, accountId: "acc-other", name: "De outra conta" }]);
      seedAuthenticatedSession();
      renderWithProviders(<App />, { route: `/accounts/${investmentAccount.id}/vaults` });

      const section = await screen.findByRole("region", { name: "Aplicações" });
      expect(within(section).getByText("CDB Inter jan/27")).toBeInTheDocument();
      expect(within(section).queryByText("De outra conta")).not.toBeInTheDocument();
      expect(within(section).getByText(/5\.234,50/)).toBeInTheDocument();
      expect(within(section).getByRole("link", { name: "Ver investimentos" })).toHaveAttribute("href", "/investments");
    });

    it("shows nothing when the account has no titles", async () => {
      handlers([]);
      seedAuthenticatedSession();
      renderWithProviders(<App />, { route: `/accounts/${investmentAccount.id}/vaults` });

      await screen.findAllByText("Reserva"); // the caixinhas have loaded
      expect(screen.queryByRole("region", { name: "Aplicações" })).not.toBeInTheDocument();
    });
  });
});
