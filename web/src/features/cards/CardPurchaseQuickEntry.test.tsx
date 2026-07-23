import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import { QuickTransactionDialog } from "@/features/transactions/QuickTransactionDialog";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import {
  cardReadHandlers,
  creditCard,
  financeReadHandlers,
  groceriesCategory,
} from "@/test/financeFixtures";
import type { CardPurchase, CardPurchaseRequest } from "@/lib/api/generated/mKFinanceAPI.schemas";

function renderDialog() {
  seedAuthenticatedSession();
  return renderWithProviders(<QuickTransactionDialog open onOpenChange={() => {}} />);
}

const purchaseResponse: CardPurchase = {
  id: "purchase-1",
  cardId: creditCard.id,
  categoryId: groceriesCategory.id,
  description: "Notebook",
  totalAmountCents: 10000,
  purchaseDate: "2026-07-18",
  installmentCount: 3,
  installments: [],
};

describe("card purchase in the quick entry", () => {
  beforeEach(() => {
    server.use(...financeReadHandlers(), ...cardReadHandlers());
  });

  it("previews the installments with the cent remainder on the last one (R1)", async () => {
    const user = userEvent.setup();
    renderDialog();

    await user.click(await screen.findByRole("tab", { name: "Cartão" }));
    await user.type(screen.getByLabelText("Valor"), "10000");
    await user.selectOptions(screen.getByLabelText("Parcelas"), "3");

    const preview = (await screen.findByText("Prévia das parcelas")).closest("div") as HTMLElement;
    // 100,00 in 3× → 33,33 + 33,33 + 33,34.
    expect(within(preview).getAllByText(/33,33/)).toHaveLength(2);
    expect(within(preview).getByText(/33,34/)).toBeInTheDocument();
  });

  it("posts a card purchase with the total, category, card and installment count", async () => {
    const purchases: CardPurchaseRequest[] = [];
    let purchasedCardId = "";
    server.use(
      http.post("*/workspaces/:workspaceId/cards/:cardId/purchases", async ({ request, params }) => {
        purchasedCardId = String(params.cardId);
        purchases.push((await request.json()) as CardPurchaseRequest);
        return HttpResponse.json(purchaseResponse, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderDialog();

    await user.click(await screen.findByRole("tab", { name: "Cartão" }));
    await user.type(screen.getByLabelText("Valor"), "10000");
    await user.type(screen.getByLabelText("Descrição"), "Notebook");
    await user.selectOptions(screen.getByLabelText("Categoria"), groceriesCategory.id);
    await user.selectOptions(screen.getByLabelText("Parcelas"), "3");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(purchases).toHaveLength(1));
    expect(purchasedCardId).toBe(creditCard.id);
    expect(purchases[0]).toMatchObject({
      categoryId: groceriesCategory.id,
      description: "Notebook",
      totalAmountCents: 10000,
      installmentCount: 3,
    });
    expect(purchases[0].purchaseDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it("requires a card before posting", async () => {
    const purchases: CardPurchaseRequest[] = [];
    server.use(
      ...cardReadHandlers({ cards: [] }),
      http.post("*/workspaces/:workspaceId/cards/:cardId/purchases", async ({ request }) => {
        purchases.push((await request.json()) as CardPurchaseRequest);
        return HttpResponse.json(purchaseResponse, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderDialog();

    await user.click(await screen.findByRole("tab", { name: "Cartão" }));
    await user.type(screen.getByLabelText("Valor"), "10000");
    await user.type(screen.getByLabelText("Descrição"), "Notebook");
    await user.selectOptions(screen.getByLabelText("Categoria"), groceriesCategory.id);
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    expect(await screen.findByText("Campo obrigatório.")).toBeInTheDocument();
    expect(purchases).toHaveLength(0);
  });
});
