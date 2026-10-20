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
  salaryCategory,
} from "@/test/financeFixtures";
import {
  TransactionStatus,
  TransactionType,
  type ReimbursementRequest,
  type Transaction,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

// ADR-0002: the boleto of 2.000 expects 1.500 back; a PIX of 500 and a cash envelope arrive later.
const boleto: Transaction = {
  id: "tx-boleto",
  accountId: checkingAccount.id,
  categoryId: groceriesCategory.id,
  type: TransactionType.EXPENSE,
  amountCents: 200000,
  date: "2026-05-10",
  description: "Boleto da casa",
  status: TransactionStatus.SETTLED,
  reimbursableCents: 150000,
  reimbursedCents: 0,
  pendingReimbursementCents: 150000,
  effectiveAmountCents: 200000,
};

const pix: Transaction = {
  id: "tx-pix",
  accountId: checkingAccount.id,
  categoryId: salaryCategory.id,
  type: TransactionType.INCOME,
  amountCents: 50000,
  date: "2026-07-20",
  description: "PIX Fulano",
  status: TransactionStatus.SETTLED,
  reimbursedCents: 0,
};

function renderPage() {
  seedAuthenticatedSession();
  return renderWithProviders(<TransactionsPage />, { route: "/transactions" });
}

describe("ReimbursementLinkDialog", () => {
  beforeEach(() => {
    server.use(
      ...financeReadHandlers({
        accounts: defaultAccounts,
        categories: defaultCategories,
        transactions: [boleto, pix],
      }),
      http.get("*/workspaces/:workspaceId/transactions/reimbursable-pending", () =>
        HttpResponse.json([boleto]),
      ),
      http.get("*/workspaces/:workspaceId/transactions/:transactionId/reimbursements", () =>
        HttpResponse.json([]),
      ),
    );
  });

  it("badges the expense with what is still to receive", async () => {
    renderPage();

    const row = (await screen.findByText(boleto.description)).closest("tr") as HTMLElement;
    expect(within(row).getByText(/A receber/)).toBeInTheDocument();
  });

  it("pre-fills the income's amount on the oldest pending expense and links it", async () => {
    const posts: { transactionId: string; body: ReimbursementRequest }[] = [];
    server.use(
      http.post(
        "*/workspaces/:workspaceId/transactions/:transactionId/reimbursements",
        async ({ request, params }) => {
          const body = (await request.json()) as ReimbursementRequest;
          posts.push({ transactionId: String(params.transactionId), body });
          return HttpResponse.json(
            {
              id: "r1",
              incomeTransactionId: pix.id,
              expenseTransactionId: boleto.id,
              amountCents: body.amountCents,
              createdAt: "2026-07-20T10:00:00Z",
            },
            { status: 201 },
          );
        },
      ),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(
      await screen.findByRole("button", { name: `Vincular ${pix.description} a despesas` }),
    );
    const dialog = await screen.findByRole("dialog");
    // R$ 500,00 (the PIX), well under the 1.500 still expected on the boleto.
    const field = await within(dialog).findByLabelText(/Valor para esta despesa/);
    await waitFor(() => expect((field as HTMLInputElement).value).toMatch(/500,00/));

    await user.click(within(dialog).getByRole("button", { name: /^Vincular/ }));

    await waitFor(() => expect(posts).toHaveLength(1));
    expect(posts[0]).toEqual({
      transactionId: pix.id,
      body: { expenseTransactionId: boleto.id, amountCents: 50000 },
    });
  });

  it("only offers the link on settled income", async () => {
    renderPage();

    await screen.findByText(pix.description);
    expect(
      screen.queryByRole("button", { name: `Vincular ${boleto.description} a despesas` }),
    ).not.toBeInTheDocument();
  });
});
