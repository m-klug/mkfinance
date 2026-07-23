import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import CardsPage from "@/features/cards/CardsPage";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import {
  archivedCard,
  cardReadHandlers,
  creditCard,
  defaultAccounts,
  defaultCards,
  financeReadHandlers,
} from "@/test/financeFixtures";
import type { CardRequest } from "@/lib/api/generated/mKFinanceAPI.schemas";

function renderPage() {
  seedAuthenticatedSession();
  return renderWithProviders(<CardsPage />, { route: "/cards" });
}

describe("CardsPage", () => {
  beforeEach(() => {
    server.use(...financeReadHandlers({ accounts: defaultAccounts }), ...cardReadHandlers());
  });

  it("lists cards with their available limit (limit − unpaid statements)", async () => {
    renderPage();

    const row = (await screen.findByText(creditCard.name)).closest("tr") as HTMLElement;
    // Full limit and available limit both render on the row.
    expect(within(row).getByText(/5\.000,00/)).toBeInTheDocument();
    expect(within(row).getByText(/3\.500,00/)).toBeInTheDocument();
    expect(within(row).getByText("20/5")).toBeInTheDocument();
  });

  it("only lists archived cards when explicitly asked", async () => {
    server.use(...cardReadHandlers({ cards: [...defaultCards, archivedCard] }));
    const user = userEvent.setup();
    renderPage();

    await screen.findByText(creditCard.name);
    expect(screen.queryByText(archivedCard.name)).not.toBeInTheDocument();

    await user.click(screen.getByLabelText("Mostrar arquivados"));

    expect(await screen.findByText(archivedCard.name)).toBeInTheDocument();
    expect(screen.getByText("Arquivado")).toBeInTheDocument();
  });

  it("creates a card, sending the limit in cents and the closing/due days", async () => {
    const createCalls: CardRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/cards", async ({ request }) => {
        createCalls.push((await request.json()) as CardRequest);
        return HttpResponse.json(creditCard, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Novo cartão" }));
    await user.type(screen.getByLabelText("Nome"), "Inter");
    await user.type(screen.getByLabelText("Limite"), "300000");
    await user.selectOptions(screen.getByLabelText("Dia de fechamento"), "15");
    await user.selectOptions(screen.getByLabelText("Dia de vencimento"), "25");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(createCalls).toHaveLength(1));
    expect(createCalls[0]).toMatchObject({
      name: "Inter",
      limitCents: 300000,
      closingDay: 15,
      dueDay: 25,
    });
  });

  it("rejects a card without a name instead of calling the API", async () => {
    const createCalls: CardRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/cards", async ({ request }) => {
        createCalls.push((await request.json()) as CardRequest);
        return HttpResponse.json(creditCard, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Novo cartão" }));
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    expect(await screen.findByText("Campo obrigatório.")).toBeInTheDocument();
    expect(createCalls).toHaveLength(0);
  });

  it("archives a card after confirmation instead of deleting it", async () => {
    let archived = false;
    server.use(
      http.post("*/workspaces/:workspaceId/cards/:cardId/archive", () => {
        archived = true;
        return HttpResponse.json({ ...creditCard, archived: true });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: `Arquivar ${creditCard.name}` }));

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/nunca são excluídos/i)).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Arquivar" }));

    await waitFor(() => expect(archived).toBe(true));
  });

  it("links each card to its statements screen", async () => {
    renderPage();

    const link = await screen.findByRole("link", { name: `Ver faturas de ${creditCard.name}` });
    expect(link).toHaveAttribute("href", `/cards/${creditCard.id}/statements`);
  });
});
