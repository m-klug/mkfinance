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
  pendingExpense,
  settledExpense,
  streamingCategory,
  transactionPage,
} from "@/test/financeFixtures";
import {
  TransactionStatus,
  type TransactionRequest,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

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
});
