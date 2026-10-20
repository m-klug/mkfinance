import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import App from "@/App";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import {
  checkingAccount,
  defaultAccounts,
  defaultCategories,
  financeReadHandlers,
  investmentAccount,
  salaryCategory,
} from "@/test/financeFixtures";
import type {
  BankStatementImportPreview,
  BankStatementImportRequest,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

const preview: BankStatementImportPreview = {
  bankId: "077",
  accountLast4: "4321",
  periodStart: "2026-09-01",
  periodEnd: "2026-09-10",
  accountBalanceCents: 250000,
  ledgerBalanceCents: 280000,
  ledgerBalanceDate: "2026-09-10",
  warnings: [],
  lines: [
    {
      externalId: "FIT-PIX",
      date: "2026-09-08",
      description: "Pix recebido - Fulano",
      type: "INCOME",
      amountCents: 50000,
      kind: "NORMAL",
      mode: "ENTRY",
      duplicate: "NONE",
      suggestedCategoryId: salaryCategory.id,
      suggestedCounterpartAccountId: null,
      suggestedStatementId: null,
      statementCandidates: [],
      selectedByDefault: true,
    },
    {
      externalId: "FIT-APP",
      date: "2026-09-08",
      description: "Aplicacao: CDB",
      type: "EXPENSE",
      amountCents: 20000,
      kind: "INVESTMENT_OUT",
      mode: "TRANSFER",
      duplicate: "NONE",
      suggestedCategoryId: null,
      suggestedCounterpartAccountId: investmentAccount.id,
      suggestedStatementId: null,
      statementCandidates: [],
      selectedByDefault: true,
    },
    {
      externalId: "FIT-BILL",
      date: "2026-09-10",
      description: "Fatura Cartao Inter",
      type: "EXPENSE",
      amountCents: 10000,
      kind: "CARD_BILL_PAYMENT",
      mode: "STATEMENT_PAYMENT",
      duplicate: "NONE",
      suggestedCategoryId: null,
      suggestedCounterpartAccountId: null,
      suggestedStatementId: "stmt-open",
      statementCandidates: [
        {
          statementId: "stmt-open",
          cardId: "card-1",
          cardName: "Inter",
          referenceMonth: "2026-09",
          dueDate: "2026-09-10",
          remainingCents: 10000,
        },
      ],
      selectedByDefault: true,
    },
    {
      externalId: "FIT-OLD",
      date: "2026-09-02",
      description: "Pix antigo",
      type: "EXPENSE",
      amountCents: 1500,
      kind: "NORMAL",
      mode: "ENTRY",
      duplicate: "EXACT",
      suggestedCategoryId: null,
      suggestedCounterpartAccountId: null,
      suggestedStatementId: null,
      statementCandidates: [],
      selectedByDefault: false,
    },
  ],
};

function renderPage() {
  seedAuthenticatedSession();
  return renderWithProviders(<App />, { route: "/transactions" });
}

describe("BankStatementImportDialog", () => {
  beforeEach(() => {
    server.use(
      ...financeReadHandlers({
        accounts: defaultAccounts,
        categories: defaultCategories,
        transactions: [],
      }),
    );
  });

  async function readOfx(user: ReturnType<typeof userEvent.setup>) {
    await user.click((await screen.findAllByRole("button", { name: "Importar extrato (OFX)" }))[0]);
    const dialog = await screen.findByRole("dialog");
    await user.upload(
      within(dialog).getByLabelText("Arquivo do extrato"),
      new File(["OFXHEADER:100"], "extrato.ofx", { type: "application/x-ofx" }),
    );
    await user.click(within(dialog).getByRole("button", { name: "Ler extrato" }));
    return dialog;
  }

  it("previews the file with each line's default mode and confirms the checked lines", async () => {
    const confirms: { accountId: string; body: BankStatementImportRequest }[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/accounts/:accountId/statement-imports/preview", () =>
        HttpResponse.json(preview),
      ),
      http.post(
        "*/workspaces/:workspaceId/accounts/:accountId/statement-imports/confirm",
        async ({ request, params }) => {
          confirms.push({
            accountId: params.accountId as string,
            body: (await request.json()) as BankStatementImportRequest,
          });
          return HttpResponse.json({ entries: 1, transfers: 1, statementPayments: 1 }, { status: 201 });
        },
      ),
    );
    const user = userEvent.setup();
    renderPage();

    const dialog = await readOfx(user);

    expect(await within(dialog).findByText(/final 4321 \(banco 077\)/)).toBeInTheDocument();
    expect(within(dialog).getByRole("checkbox", { name: "Pix recebido - Fulano" })).toBeChecked();
    expect(within(dialog).getByRole("checkbox", { name: "Pix antigo" })).not.toBeChecked();
    expect(within(dialog).getByText("Já importado")).toBeInTheDocument();
    expect(within(dialog).getByLabelText("Categoria", { selector: "#bank-import-category-0" })).toHaveValue(
      salaryCategory.id,
    );
    expect(
      within(dialog).getByLabelText("Conta de destino", { selector: "#bank-import-counterpart-1" }),
    ).toHaveValue(investmentAccount.id);
    expect(within(dialog).getByLabelText("Fatura", { selector: "#bank-import-statement-2" })).toHaveValue(
      "stmt-open",
    );

    await user.click(within(dialog).getByRole("button", { name: "Importar 3 lançamentos" }));

    await waitFor(() => expect(confirms).toHaveLength(1));
    expect(confirms[0].accountId).toBe(checkingAccount.id);
    expect(confirms[0].body.items).toEqual([
      {
        externalId: "FIT-PIX",
        mode: "ENTRY",
        type: "INCOME",
        amountCents: 50000,
        date: "2026-09-08",
        description: "Pix recebido - Fulano",
        categoryId: salaryCategory.id,
        counterpartAccountId: null,
        contributionPillar: null,
        statementId: null,
      },
      {
        externalId: "FIT-APP",
        mode: "TRANSFER",
        type: "EXPENSE",
        amountCents: 20000,
        date: "2026-09-08",
        description: "Aplicacao: CDB",
        categoryId: null,
        counterpartAccountId: investmentAccount.id,
        contributionPillar: null,
        statementId: null,
      },
      {
        externalId: "FIT-BILL",
        mode: "STATEMENT_PAYMENT",
        type: "EXPENSE",
        amountCents: 10000,
        date: "2026-09-10",
        description: "Fatura Cartao Inter",
        categoryId: null,
        counterpartAccountId: null,
        contributionPillar: null,
        statementId: "stmt-open",
      },
    ]);
  });

  it("blocks the import while a checked line is incomplete", async () => {
    server.use(
      http.post("*/workspaces/:workspaceId/accounts/:accountId/statement-imports/preview", () =>
        HttpResponse.json(preview),
      ),
    );
    const user = userEvent.setup();
    renderPage();

    const dialog = await readOfx(user);
    await within(dialog).findByDisplayValue("Pix antigo");
    // The old Pix is an expense with no suggested category: checking it leaves it incomplete.
    await user.click(within(dialog).getByRole("checkbox", { name: "Pix antigo" }));

    expect(
      within(dialog).getByText("Complete categoria, conta ou fatura de todos os lançamentos marcados."),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Importar 4 lançamentos" })).toBeDisabled();
  });

  it("switches a line to another mode and asks for what that mode needs", async () => {
    server.use(
      http.post("*/workspaces/:workspaceId/accounts/:accountId/statement-imports/preview", () =>
        HttpResponse.json(preview),
      ),
    );
    const user = userEvent.setup();
    renderPage();

    const dialog = await readOfx(user);
    await within(dialog).findByDisplayValue("Aplicacao: CDB");
    await user.selectOptions(
      within(dialog).getByLabelText("Como registrar", { selector: "#bank-import-mode-1" }),
      "ENTRY",
    );

    expect(within(dialog).getByLabelText("Categoria", { selector: "#bank-import-category-1" })).toHaveValue("");
    expect(within(dialog).getByRole("button", { name: "Importar 3 lançamentos" })).toBeDisabled();
  });

  it("shows the translated error for a file that is not an OFX", async () => {
    server.use(
      http.post("*/workspaces/:workspaceId/accounts/:accountId/statement-imports/preview", () =>
        HttpResponse.json({ code: "OFX_UNREADABLE" }, { status: 422 }),
      ),
    );
    const user = userEvent.setup();
    renderPage();

    await readOfx(user);

    expect(await screen.findByText("Não foi possível ler este arquivo como um extrato OFX.")).toBeInTheDocument();
  });
});
