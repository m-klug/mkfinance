import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import App from "@/App";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import {
  cardReadHandlers,
  checkingAccount,
  creditCard,
  defaultAccounts,
  defaultCategories,
  financeReadHandlers,
  groceriesCategory,
  openStatement,
  openStatementDetail,
} from "@/test/financeFixtures";
import {
  StatementStatus,
  type Statement,
  type StatementDetail,
  type StatementPaymentRequest,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

function monthNow(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function todayIso(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(
    now.getDate(),
  ).padStart(2, "0")}`;
}

// Statements default to the running month, so the fixtures are pinned to it to
// keep the tests independent of the calendar.
const currentStatement: Statement = { ...openStatement, referenceMonth: monthNow() };
const currentDetail: StatementDetail = { ...openStatementDetail, referenceMonth: monthNow() };

function renderStatements(cardId = creditCard.id) {
  seedAuthenticatedSession();
  return renderWithProviders(<App />, { route: `/cards/${cardId}/statements` });
}

describe("CardStatementsPage", () => {
  beforeEach(() => {
    server.use(
      ...financeReadHandlers({ accounts: defaultAccounts, categories: defaultCategories }),
      ...cardReadHandlers({ statements: [currentStatement], detail: currentDetail }),
    );
  });

  it("renders statement items with the installment number and status", async () => {
    renderStatements();

    const row = (await screen.findByText("Notebook")).closest("tr") as HTMLElement;
    expect(within(row).getByText("1/3")).toBeInTheDocument();
    expect(within(row).getByText(groceriesCategory.name)).toBeInTheDocument();
    expect(within(row).getByText(/33,33/)).toBeInTheDocument();
    // OPEN statement badge.
    expect(screen.getByText("Aberta")).toBeInTheDocument();
  });

  it("navigates to a month with no statement", async () => {
    const user = userEvent.setup();
    renderStatements();

    await screen.findByText("Notebook");
    await user.click(screen.getByRole("button", { name: "Próximo mês" }));

    expect(await screen.findByText("Sem fatura neste mês.")).toBeInTheDocument();
  });

  it("pays the whole statement, defaulting the amount to the remaining balance", async () => {
    const payments: StatementPaymentRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/statements/:statementId/payments", async ({ request }) => {
        payments.push((await request.json()) as StatementPaymentRequest);
        return HttpResponse.json({ ...currentStatement, paidCents: 10000, status: StatementStatus.PAID });
      }),
    );
    const user = userEvent.setup();
    renderStatements();

    await user.click(await screen.findByRole("button", { name: "Pagar fatura" }));

    const dialog = await screen.findByRole("dialog");
    // Default payment account follows the card's default (checking).
    expect(within(dialog).getByLabelText("Conta")).toHaveValue(checkingAccount.id);
    await user.click(within(dialog).getByRole("button", { name: "Pagar" }));

    await waitFor(() => expect(payments).toHaveLength(1));
    expect(payments[0]).toMatchObject({ accountId: checkingAccount.id, amountCents: 10000 });
    expect(payments[0].paymentDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it("supports a partial payment below the remaining balance", async () => {
    const payments: StatementPaymentRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/statements/:statementId/payments", async ({ request }) => {
        payments.push((await request.json()) as StatementPaymentRequest);
        return HttpResponse.json({ ...currentStatement, paidCents: 5000, status: StatementStatus.PARTIAL });
      }),
    );
    const user = userEvent.setup();
    renderStatements();

    await user.click(await screen.findByRole("button", { name: "Pagar fatura" }));
    const dialog = await screen.findByRole("dialog");
    const amount = within(dialog).getByLabelText("Valor");
    await user.clear(amount);
    await user.type(amount, "5000");
    await user.click(within(dialog).getByRole("button", { name: "Pagar" }));

    await waitFor(() => expect(payments).toHaveLength(1));
    expect(payments[0].amountCents).toBe(5000);
  });

  it("blocks a payment larger than the remaining balance", async () => {
    const payments: StatementPaymentRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/statements/:statementId/payments", async ({ request }) => {
        payments.push((await request.json()) as StatementPaymentRequest);
        return HttpResponse.json(currentStatement, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderStatements();

    await user.click(await screen.findByRole("button", { name: "Pagar fatura" }));
    const dialog = await screen.findByRole("dialog");
    const amount = within(dialog).getByLabelText("Valor");
    await user.clear(amount);
    await user.type(amount, "999999");
    await user.click(within(dialog).getByRole("button", { name: "Pagar" }));

    expect(await screen.findByText("O valor excede o restante da fatura.")).toBeInTheDocument();
    expect(payments).toHaveLength(0);
  });

  it("reverses a payment on a paid statement (§6.5)", async () => {
    const paidStatement: Statement = {
      ...currentStatement,
      paidCents: 10000,
      status: StatementStatus.PAID,
    };
    const paidDetail: StatementDetail = {
      ...currentDetail,
      paidCents: 10000,
      status: StatementStatus.PAID,
      payments: [
        {
          id: "pay-1",
          accountId: checkingAccount.id,
          amountCents: 10000,
          paymentDate: "2026-08-01",
          transactionId: "tx-pay-1",
        },
      ],
    };
    const reversed: string[] = [];
    server.use(
      ...cardReadHandlers({ statements: [paidStatement], detail: paidDetail }),
      http.delete(
        "*/workspaces/:workspaceId/statements/:statementId/payments/:paymentId",
        ({ params }) => {
          reversed.push(String(params.paymentId));
          return new HttpResponse(null, { status: 204 });
        },
      ),
    );
    const user = userEvent.setup();
    renderStatements();

    // A paid statement no longer offers to pay, but its payment can be undone.
    await screen.findByText("Notebook");
    expect(screen.queryByRole("button", { name: "Pagar fatura" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /^Estornar pagamento de/ }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Estornar" }));

    await waitFor(() => expect(reversed).toEqual(["pay-1"]));
  });

  it("flags a bill whose due date is near", async () => {
    const dueSoon: Statement = { ...currentStatement, dueDate: todayIso() };
    server.use(...cardReadHandlers({ statements: [dueSoon], detail: currentDetail }));
    renderStatements();

    expect(await screen.findByText("Vence em breve")).toBeInTheDocument();
  });
});
