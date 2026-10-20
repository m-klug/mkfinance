import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import App from "@/App";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import {
  cardReadHandlers,
  creditCard,
  defaultAccounts,
  defaultCategories,
  financeReadHandlers,
  groceriesCategory,
  openStatement,
  openStatementDetail,
} from "@/test/financeFixtures";
import type {
  CardPurchaseBatchRequest,
  StatementImportPreview,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

const preview: StatementImportPreview = {
  statementMonth: "2026-09",
  dueDate: "2026-09-10",
  totalCents: 25000,
  warnings: [{ code: "PAYMENT_IGNORED", detail: "2148" }],
  lines: [
    {
      cardLast4: "5576",
      date: "2026-08-03",
      description: "PADARIA",
      amountCents: 20000,
      kind: "EXPENSE",
      status: "NEW",
      duplicate: false,
      externalId: "fp:padaria",
      candidates: [],
      suggestedCategoryId: groceriesCategory.id,
    },
    {
      cardLast4: "5576",
      date: "2026-08-05",
      description: "LOJA JA LANCADA",
      amountCents: 5000,
      kind: "EXPENSE",
      status: "ALREADY_IMPORTED",
      duplicate: true,
      externalId: "fp:loja",
      candidates: [],
      suggestedCategoryId: null,
    },
    {
      cardLast4: "5576",
      date: "2026-08-06",
      description: "ESTORNO X",
      amountCents: 1000,
      kind: "CREDIT",
      status: "NEW",
      duplicate: false,
      externalId: "fp:estorno",
      candidates: [],
      suggestedCategoryId: null,
    },
  ],
};

function renderStatements() {
  seedAuthenticatedSession();
  return renderWithProviders(<App />, { route: `/cards/${creditCard.id}/statements` });
}

describe("StatementImportDialog", () => {
  beforeEach(() => {
    server.use(
      ...financeReadHandlers({ accounts: defaultAccounts, categories: defaultCategories }),
      ...cardReadHandlers({ statements: [openStatement], detail: openStatementDetail }),
    );
  });

  async function readPdf(user: ReturnType<typeof userEvent.setup>) {
    await user.click((await screen.findAllByRole("button", { name: "Importar PDF" }))[0]);
    const dialog = await screen.findByRole("dialog");
    await user.upload(
      within(dialog).getByLabelText("Arquivo da fatura"),
      new File(["%PDF"], "fatura.pdf", { type: "application/pdf" }),
    );
    await user.type(within(dialog).getByLabelText("Senha do PDF"), "123456");
    await user.click(within(dialog).getByRole("button", { name: "Ler PDF" }));
    return dialog;
  }

  it("previews the PDF, leaves duplicates and credits unchecked, and imports the checked rows", async () => {
    const batches: CardPurchaseBatchRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/cards/:cardId/statement-imports/preview", () =>
        HttpResponse.json(preview),
      ),
      http.post("*/workspaces/:workspaceId/cards/:cardId/purchases/batch", async ({ request }) => {
        batches.push((await request.json()) as CardPurchaseBatchRequest);
        return HttpResponse.json([], { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderStatements();

    const dialog = await readPdf(user);

    expect(await within(dialog).findByText("PADARIA")).toBeInTheDocument();
    expect(within(dialog).getByRole("checkbox", { name: "PADARIA" })).toBeChecked();
    expect(within(dialog).getByRole("checkbox", { name: "LOJA JA LANCADA" })).not.toBeChecked();
    expect(within(dialog).getByRole("checkbox", { name: "ESTORNO X" })).toBeDisabled();
    expect(within(dialog).getByText("Já lançada")).toBeInTheDocument();
    // The suggested category is pre-selected.
    expect(within(dialog).getByLabelText("Categoria", { selector: "#import-category-0" })).toHaveValue(
      groceriesCategory.id,
    );

    await user.click(within(dialog).getByRole("button", { name: "Importar 1 lançamento" }));

    await waitFor(() => expect(batches).toHaveLength(1));
    expect(batches[0]).toEqual({
      statementMonth: "2026-09",
      items: [
        {
          categoryId: groceriesCategory.id,
          description: "PADARIA",
          totalAmountCents: 20000,
          purchaseDate: "2026-08-03",
          externalId: "fp:padaria",
        },
      ],
    });
  });

  it("links a recognized line to the user's own entry instead of creating a purchase", async () => {
    const batches: CardPurchaseBatchRequest[] = [];
    const matched: StatementImportPreview = {
      ...preview,
      lines: [
        {
          cardLast4: "5576",
          date: "2026-08-03",
          description: "PASTELARIA ZEZINHO",
          amountCents: 2000,
          kind: "EXPENSE",
          status: "MATCH",
          duplicate: false,
          externalId: "fp:pastel",
          candidates: [
            { installmentId: "inst-1", description: "Pastel com os amigos", purchaseDate: "2026-08-03" },
          ],
          suggestedCategoryId: null,
        },
      ],
    };
    server.use(
      http.post("*/workspaces/:workspaceId/cards/:cardId/statement-imports/preview", () =>
        HttpResponse.json(matched),
      ),
      http.post("*/workspaces/:workspaceId/cards/:cardId/purchases/batch", async ({ request }) => {
        batches.push((await request.json()) as CardPurchaseBatchRequest);
        return HttpResponse.json([], { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderStatements();

    const dialog = await readPdf(user);

    expect(await within(dialog).findByText("Parece ser um lançamento seu")).toBeInTheDocument();
    // Linked by default, so no category is asked for.
    expect(within(dialog).queryByLabelText("Categoria")).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Importar 1 lançamento" }));

    await waitFor(() => expect(batches).toHaveLength(1));
    expect(batches[0].items).toEqual([
      {
        description: "PASTELARIA ZEZINHO",
        totalAmountCents: 2000,
        purchaseDate: "2026-08-03",
        externalId: "fp:pastel",
        linkInstallmentId: "inst-1",
      },
    ]);
  });

  it("blocks the import while a checked row has no category", async () => {
    server.use(
      http.post("*/workspaces/:workspaceId/cards/:cardId/statement-imports/preview", () =>
        HttpResponse.json(preview),
      ),
    );
    const user = userEvent.setup();
    renderStatements();

    const dialog = await readPdf(user);
    await within(dialog).findByText("PADARIA");
    await user.click(within(dialog).getByRole("checkbox", { name: "LOJA JA LANCADA" }));

    expect(
      within(dialog).getByText("Escolha a categoria de todos os lançamentos marcados."),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "Importar 2 lançamentos" })).toBeDisabled();
  });

  it("shows the translated error when the password is wrong", async () => {
    server.use(
      http.post("*/workspaces/:workspaceId/cards/:cardId/statement-imports/preview", () =>
        HttpResponse.json({ code: "PDF_PASSWORD_INVALID" }, { status: 422 }),
      ),
    );
    const user = userEvent.setup();
    renderStatements();

    await readPdf(user);

    expect(await screen.findByText("Senha do PDF incorreta.")).toBeInTheDocument();
  });
});
