import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import TransactionsPage from "@/features/transactions/TransactionsPage";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import {
  checkingAccount,
  defaultAccounts,
  defaultCategories,
  financeReadHandlers,
  groceriesCategory,
  groceriesTag,
  investmentAccount,
  pendingExpense,
  settledExpense,
  streamingCategory,
  taggedExpense,
  transactionPage,
  urgentTag,
} from "@/test/financeFixtures";
import {
  ContributionPillar,
  TransactionStatus,
  TransactionType,
  TransferDirection,
  type Transaction,
  type TransactionRequest,
  type TransferUpdateRequest,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

// A contribution is the IN leg of a transfer landing in an investment account
// (R9), so it is the case that exercises both the transfer edit path and the
// pillar re-targeting selector.
const contributionLeg: Transaction = {
  id: "tx-transfer-in",
  accountId: investmentAccount.id,
  categoryId: null,
  type: TransactionType.TRANSFER,
  amountCents: 100000,
  date: "2026-07-10",
  description: "Aporte mensal",
  status: TransactionStatus.SETTLED,
  transferPairId: "tx-transfer-out",
  transferDirection: TransferDirection.IN,
  contributionPillar: ContributionPillar.GOALS,
};

function renderPage() {
  seedAuthenticatedSession();
  return renderWithProviders(<TransactionsPage />, { route: "/transactions" });
}

/** Read handlers that record the query string the screen sent. */
function listHandlerCapturing(sink: URLSearchParams[]) {
  return http.get("*/workspaces/:workspaceId/transactions", ({ request }) => {
    sink.push(new URL(request.url).searchParams);
    return HttpResponse.json(transactionPage([settledExpense, pendingExpense]));
  });
}

describe("TransactionsPage", () => {
  beforeEach(() => {
    server.use(
      ...financeReadHandlers({
        accounts: defaultAccounts,
        categories: defaultCategories,
        transactions: [settledExpense, pendingExpense],
      }),
    );
  });

  it("lists transactions with account, category, signed amount and status", async () => {
    renderPage();

    const row = (await screen.findByText(settledExpense.description)).closest("tr") as HTMLElement;
    expect(within(row).getByText(checkingAccount.name)).toBeInTheDocument();
    expect(within(row).getByText(groceriesCategory.name)).toBeInTheDocument();
    // Expenses render negative.
    expect(within(row).getByText(/-R\$\s*159,90/)).toBeInTheDocument();
    expect(within(row).getByText("Efetivado")).toBeInTheDocument();

    const pendingRow = screen.getByText(pendingExpense.description).closest("tr") as HTMLElement;
    expect(within(pendingRow).getByText("Pendente")).toBeInTheDocument();
  });

  it("settles a pending transaction in a single click (R5)", async () => {
    const settled: string[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/transactions/:transactionId/settle", ({ params }) => {
        settled.push(String(params.transactionId));
        return HttpResponse.json({ ...pendingExpense, status: TransactionStatus.SETTLED });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", {
        name: `Efetivar ${pendingExpense.description}`,
      }),
    );

    await waitFor(() => expect(settled).toEqual([pendingExpense.id]));
  });

  it("offers no settle action on an already settled transaction", async () => {
    renderPage();

    await screen.findByText(settledExpense.description);
    expect(
      screen.queryByRole("button", { name: `Efetivar ${settledExpense.description}` }),
    ).not.toBeInTheDocument();
  });

  it("sends the period, account, type and text filters to the API", async () => {
    const queries: URLSearchParams[] = [];
    server.use(listHandlerCapturing(queries));
    const user = userEvent.setup();
    renderPage();

    await screen.findByText(settledExpense.description);

    await user.clear(screen.getByLabelText("De"));
    await user.type(screen.getByLabelText("De"), "2026-07-01");
    await user.selectOptions(screen.getByLabelText("Conta"), checkingAccount.id);
    await user.selectOptions(screen.getByLabelText("Tipo"), "EXPENSE");
    await user.selectOptions(screen.getByLabelText("Situação"), "PENDING");
    await user.type(screen.getByLabelText("Buscar na descrição"), "mercado");

    await waitFor(() => {
      const last = queries[queries.length - 1];
      expect(last.get("from")).toBe("2026-07-01");
      expect(last.get("accountId")).toBe(checkingAccount.id);
      expect(last.get("type")).toBe("EXPENSE");
      expect(last.get("status")).toBe("PENDING");
      expect(last.get("search")).toBe("mercado");
    });
  });

  it("edits a transaction from the prefilled dialog", async () => {
    const updates: TransactionRequest[] = [];
    server.use(
      http.patch("*/workspaces/:workspaceId/transactions/:transactionId", async ({ request }) => {
        updates.push((await request.json()) as TransactionRequest);
        return HttpResponse.json(settledExpense);
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: `Editar ${settledExpense.description}` }),
    );

    // The filter bar also has a "Categoria" field, so scope to the dialog.
    const dialog = await screen.findByRole("dialog");
    const description = within(dialog).getByLabelText("Descrição");
    expect(description).toHaveValue(settledExpense.description);
    expect(within(dialog).getByLabelText("Categoria")).toHaveValue(groceriesCategory.id);

    await user.clear(description);
    await user.type(description, "Compra da semana");
    await user.selectOptions(within(dialog).getByLabelText("Categoria"), streamingCategory.id);
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(updates).toHaveLength(1));
    expect(updates[0]).toMatchObject({
      description: "Compra da semana",
      categoryId: streamingCategory.id,
      amountCents: settledExpense.amountCents,
      type: "EXPENSE",
    });
  });

  it("rejects an edit that empties the description", async () => {
    const updates: TransactionRequest[] = [];
    server.use(
      http.patch("*/workspaces/:workspaceId/transactions/:transactionId", async ({ request }) => {
        updates.push((await request.json()) as TransactionRequest);
        return HttpResponse.json(settledExpense);
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: `Editar ${settledExpense.description}` }),
    );
    await user.clear(screen.getByLabelText("Descrição"));
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    expect(await screen.findByText("Campo obrigatório.")).toBeInTheDocument();
    expect(updates).toHaveLength(0);
  });

  it("deletes a transaction after confirmation", async () => {
    const deleted: string[] = [];
    server.use(
      http.delete("*/workspaces/:workspaceId/transactions/:transactionId", ({ params }) => {
        deleted.push(String(params.transactionId));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: `Excluir ${settledExpense.description}` }),
    );
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Excluir" }));

    await waitFor(() => expect(deleted).toEqual([settledExpense.id]));
  });

  it("shows an empty state when the period has no entries", async () => {
    server.use(...financeReadHandlers({ transactions: [] }));
    renderPage();

    expect(await screen.findByText("Nenhum lançamento no período.")).toBeInTheDocument();
  });

  // §6.6: transfer legs are editable again through the dedicated PATCH /transfers
  // endpoint — the edit button is no longer hidden on TRANSFER rows.
  it("edits a transfer leg through the dedicated transfer endpoint", async () => {
    server.use(
      ...financeReadHandlers({
        accounts: defaultAccounts,
        categories: defaultCategories,
        transactions: [contributionLeg],
      }),
    );
    const updates: TransferUpdateRequest[] = [];
    let patchedId = "";
    server.use(
      http.patch("*/workspaces/:workspaceId/transfers/:transactionId", async ({ request, params }) => {
        patchedId = String(params.transactionId);
        updates.push((await request.json()) as TransferUpdateRequest);
        return HttpResponse.json(contributionLeg);
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Editar Aporte mensal" }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Editar transferência")).toBeInTheDocument();
    // A transfer has no category, but a contribution leg re-targets its pillar.
    expect(within(dialog).queryByLabelText("Categoria")).not.toBeInTheDocument();
    expect(within(dialog).getByLabelText("Pilar do aporte")).toHaveValue(ContributionPillar.GOALS);

    const amount = within(dialog).getByLabelText("Valor");
    await user.clear(amount);
    await user.type(amount, "120000");
    await user.selectOptions(
      within(dialog).getByLabelText("Pilar do aporte"),
      ContributionPillar.FINANCIAL_FREEDOM,
    );
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(updates).toHaveLength(1));
    expect(patchedId).toBe(contributionLeg.id);
    expect(updates[0]).toMatchObject({
      amountCents: 120000,
      contributionPillar: ContributionPillar.FINANCIAL_FREEDOM,
    });
  });

  // BE-8/FE-7: tags in the extrato.
  it("shows the tags assigned to a transaction in its row", async () => {
    server.use(...financeReadHandlers({ transactions: [taggedExpense] }));
    renderPage();

    const row = (await screen.findByText(taggedExpense.description)).closest(
      "tr",
    ) as HTMLElement;
    expect(within(row).getByText(groceriesTag.name)).toBeInTheDocument();
    expect(within(row).getByText(urgentTag.name)).toBeInTheDocument();
  });

  it("assigns tags to a transaction from the edit dialog", async () => {
    const updates: TransactionRequest[] = [];
    server.use(
      ...financeReadHandlers({ transactions: [taggedExpense] }),
      http.patch("*/workspaces/:workspaceId/transactions/:transactionId", async ({ request }) => {
        updates.push((await request.json()) as TransactionRequest);
        return HttpResponse.json(taggedExpense);
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: `Editar ${taggedExpense.description}` }),
    );

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(groceriesTag.name)).toBeInTheDocument();
    expect(within(dialog).getByText(urgentTag.name)).toBeInTheDocument();

    await user.click(within(dialog).getByLabelText(`Remover tag ${urgentTag.name}`));
    await user.type(within(dialog).getByLabelText("Tags"), "viagem{Enter}");
    await user.click(within(dialog).getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(updates).toHaveLength(1));
    expect(updates[0].tags).toEqual([groceriesTag.name, "viagem"]);
  });

  it("filters by tag and restores the full list when the filter is cleared", async () => {
    server.use(
      ...financeReadHandlers({ transactions: [settledExpense, taggedExpense] }),
    );
    const user = userEvent.setup();
    renderPage();

    await screen.findByText(settledExpense.description);
    await screen.findByText(taggedExpense.description);

    await user.type(screen.getByLabelText("Tags"), `${groceriesTag.name}{Enter}`);

    await waitFor(() => {
      expect(screen.queryByText(settledExpense.description)).not.toBeInTheDocument();
      expect(screen.getByText(taggedExpense.description)).toBeInTheDocument();
    });

    await user.click(screen.getByLabelText(`Remover tag ${groceriesTag.name}`));

    await waitFor(() => {
      expect(screen.getByText(settledExpense.description)).toBeInTheDocument();
      expect(screen.getByText(taggedExpense.description)).toBeInTheDocument();
    });
  });
});
