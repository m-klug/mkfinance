import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import CategoriesPage from "@/features/categories/CategoriesPage";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import {
  defaultCategories,
  financeReadHandlers,
  groceriesCategory,
  salaryCategory,
  streamingCategory,
} from "@/test/financeFixtures";
import {
  CategoryType,
  Pillar,
  type Category,
  type CategoryRequest,
} from "@/lib/api/generated/mKFinanceAPI.schemas";

const supermarketSubcategory: Category = {
  id: "cat-supermarket",
  name: "Supermercado",
  type: CategoryType.EXPENSE,
  pillar: Pillar.FIXED_COSTS,
  parentId: groceriesCategory.id,
  system: false,
};

function renderPage() {
  seedAuthenticatedSession();
  return renderWithProviders(<CategoriesPage />, { route: "/categories" });
}

/** Finds the card of a pillar by its heading, to assert scoped content. */
function pillarCard(pillar: string): HTMLElement {
  return screen.getByText(pillar).closest("[data-slot=card]") as HTMLElement;
}

describe("CategoriesPage", () => {
  beforeEach(() => {
    server.use(
      ...financeReadHandlers({ categories: [...defaultCategories, supermarketSubcategory] }),
    );
  });

  it("groups expense categories under their pillar, with subcategories nested", async () => {
    renderPage();

    await screen.findByText(groceriesCategory.name);

    const fixedCosts = pillarCard("Custos Fixos");
    expect(within(fixedCosts).getByText(groceriesCategory.name)).toBeInTheDocument();
    expect(within(fixedCosts).getByText(supermarketSubcategory.name)).toBeInTheDocument();

    const pleasures = pillarCard("Prazeres");
    expect(within(pleasures).getByText(streamingCategory.name)).toBeInTheDocument();

    // Empty pillars still render, so the six-pillar method stays visible.
    expect(within(pillarCard("Conhecimento")).getByText("Nenhuma categoria neste pilar.")).toBeInTheDocument();
  });

  it("keeps income categories in their own tab and without a pillar", async () => {
    const user = userEvent.setup();
    renderPage();

    await screen.findByText(groceriesCategory.name);
    expect(screen.queryByText(salaryCategory.name)).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Receitas" }));

    expect(await screen.findByText(salaryCategory.name)).toBeInTheDocument();
  });

  it("marks system categories as non-editable", async () => {
    renderPage();

    await screen.findByText("Pagamento de fatura");
    expect(screen.getByText("Sistema")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Excluir Pagamento de fatura" }),
    ).not.toBeInTheDocument();
  });

  it("requires a pillar on an expense category before calling the API", async () => {
    const createCalls: CategoryRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/categories", async ({ request }) => {
        createCalls.push((await request.json()) as CategoryRequest);
        return HttpResponse.json(groceriesCategory, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Nova categoria" }));
    await user.type(screen.getByLabelText("Nome"), "Farmácia");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    expect(await screen.findByText("Campo obrigatório.")).toBeInTheDocument();
    expect(createCalls).toHaveLength(0);

    await user.selectOptions(screen.getByLabelText("Pilar"), Pillar.COMFORT);
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(createCalls).toHaveLength(1));
    expect(createCalls[0]).toMatchObject({
      name: "Farmácia",
      type: CategoryType.EXPENSE,
      pillar: Pillar.COMFORT,
      parentId: null,
    });
  });

  it("hides the pillar field for income categories and sends it as null", async () => {
    const createCalls: CategoryRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/categories", async ({ request }) => {
        createCalls.push((await request.json()) as CategoryRequest);
        return HttpResponse.json(salaryCategory, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Nova categoria" }));
    await user.selectOptions(screen.getByLabelText("Tipo"), CategoryType.INCOME);

    expect(screen.queryByLabelText("Pilar")).not.toBeInTheDocument();

    await user.type(screen.getByLabelText("Nome"), "Freelas");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(createCalls).toHaveLength(1));
    expect(createCalls[0].pillar).toBeNull();
  });

  it("creates a subcategory under the selected parent", async () => {
    const createCalls: CategoryRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/categories", async ({ request }) => {
        createCalls.push((await request.json()) as CategoryRequest);
        return HttpResponse.json(groceriesCategory, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Nova categoria" }));
    await user.type(screen.getByLabelText("Nome"), "Feira");
    await user.selectOptions(screen.getByLabelText("Pilar"), Pillar.FIXED_COSTS);
    await user.selectOptions(screen.getByLabelText("Categoria pai"), groceriesCategory.id);
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(createCalls).toHaveLength(1));
    expect(createCalls[0].parentId).toBe(groceriesCategory.id);
  });

  it("deletes a category after confirmation", async () => {
    let deleted = false;
    server.use(
      http.delete("*/workspaces/:workspaceId/categories/:categoryId", () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Excluir Streaming" }));

    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Excluir" }));

    await waitFor(() => expect(deleted).toBe(true));
  });
});
