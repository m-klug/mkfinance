import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import RecurrencesPage from "@/features/recurrences/RecurrencesPage";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import { financeReadHandlers, groceriesCategory } from "@/test/financeFixtures";
import {
  closedGymRecurrence,
  futureSalaryRecurrence,
  recurrenceReadHandlers,
  rentRecurrence,
} from "@/features/recurrences/recurrenceFixtures";
import type { RecurrenceRequest } from "@/lib/api/generated/mKFinanceAPI.schemas";

function renderPage() {
  seedAuthenticatedSession();
  return renderWithProviders(<RecurrencesPage />, { route: "/recurrences" });
}

// The page renders a desktop table (sm:block) and a mobile list (sm:hidden)
// for the same data — CSS toggles which is visible, but both exist in jsdom.
// Scope row-content queries to the desktop table to avoid duplicate matches.
async function desktopTable() {
  return within(await screen.findByTestId("recurrences-desktop-table"));
}

describe("RecurrencesPage", () => {
  beforeEach(() => {
    server.use(...financeReadHandlers(), ...recurrenceReadHandlers());
  });

  it("lists only active series by default", async () => {
    renderPage();

    const table = await desktopTable();
    expect(await table.findByText(rentRecurrence.description)).toBeInTheDocument();
    expect(table.getByText(futureSalaryRecurrence.description)).toBeInTheDocument();
    expect(table.queryByText(closedGymRecurrence.description)).not.toBeInTheDocument();
  });

  it("reveals closed series, without edit/delete actions, when asked", async () => {
    const user = userEvent.setup();
    renderPage();

    await (await desktopTable()).findByText(rentRecurrence.description);
    await user.click(screen.getByLabelText("Mostrar encerradas"));

    // The table remounts while the re-filtered query is loading, so re-grab it.
    const table = await desktopTable();
    expect(await table.findByText(closedGymRecurrence.description)).toBeInTheDocument();
    expect(table.getByText("Encerrada")).toBeInTheDocument();
    expect(
      table.queryByRole("button", { name: `Editar ${closedGymRecurrence.description}` }),
    ).not.toBeInTheDocument();
  });

  it("switches the schedule fields shown as the frequency changes", async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Nova recorrência" }));

    expect(screen.getByLabelText("Dia do mês")).toBeInTheDocument();
    expect(screen.queryByLabelText("Dia da semana")).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Frequência"), "WEEKLY");
    expect(screen.queryByLabelText("Dia do mês")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Dia da semana")).toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText("Frequência"), "YEARLY");
    expect(screen.getByLabelText("Dia do mês")).toBeInTheDocument();
    expect(screen.getByLabelText("Mês do ano (1-12)")).toBeInTheDocument();
  });

  it("requires the day of month before creating a MONTHLY series", async () => {
    const createCalls: RecurrenceRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/recurrences", async ({ request }) => {
        createCalls.push((await request.json()) as RecurrenceRequest);
        return HttpResponse.json(rentRecurrence, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Nova recorrência" }));
    await user.type(screen.getByLabelText("Descrição"), "Internet");
    await user.type(screen.getByLabelText("Valor"), "9990");
    await user.selectOptions(screen.getByLabelText("Categoria"), groceriesCategory.id);
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    expect(await screen.findByText("Campo obrigatório.")).toBeInTheDocument();
    expect(createCalls).toHaveLength(0);
  });

  it("creates a monthly expense series with the fields filled", async () => {
    const createCalls: RecurrenceRequest[] = [];
    server.use(
      http.post("*/workspaces/:workspaceId/recurrences", async ({ request }) => {
        createCalls.push((await request.json()) as RecurrenceRequest);
        return HttpResponse.json(rentRecurrence, { status: 201 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Nova recorrência" }));
    await user.type(screen.getByLabelText("Descrição"), "Internet");
    await user.type(screen.getByLabelText("Valor"), "9990");
    await user.selectOptions(screen.getByLabelText("Categoria"), groceriesCategory.id);
    await user.type(screen.getByLabelText("Dia do mês"), "15");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(createCalls).toHaveLength(1));
    expect(createCalls[0]).toMatchObject({
      type: "EXPENSE",
      description: "Internet",
      amountCents: 9990,
      categoryId: groceriesCategory.id,
      frequency: "MONTHLY",
      dayOfMonth: 15,
      dayOfWeek: null,
      monthOfYear: null,
    });
  });

  it("edits a series that never started (no scope dialog) directly", async () => {
    const updateCalls: { data: RecurrenceRequest; url: string }[] = [];
    server.use(
      http.patch("*/workspaces/:workspaceId/recurrences/:recurrenceId", async ({ request }) => {
        updateCalls.push({ data: (await request.json()) as RecurrenceRequest, url: request.url });
        return HttpResponse.json(futureSalaryRecurrence);
      }),
    );
    const user = userEvent.setup();
    renderPage();

    const table = await desktopTable();
    await user.click(
      await table.findByRole("button", { name: `Editar ${futureSalaryRecurrence.description}` }),
    );

    // No scope prompt — the series' startDate is in the future.
    expect(screen.queryByText("Qual parte da série editar?")).not.toBeInTheDocument();
    const descriptionInput = await screen.findByLabelText("Descrição");
    expect(descriptionInput).toHaveValue(futureSalaryRecurrence.description);

    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(updateCalls).toHaveLength(1));
    expect(new URL(updateCalls[0].url).searchParams.get("scope")).toBeNull();
  });

  it("asks the scope before editing a series that may have materialized, and honours THIS_ONE", async () => {
    const updateCalls: { data: RecurrenceRequest; url: string }[] = [];
    server.use(
      http.patch("*/workspaces/:workspaceId/recurrences/:recurrenceId", async ({ request }) => {
        updateCalls.push({ data: (await request.json()) as RecurrenceRequest, url: request.url });
        return HttpResponse.json(rentRecurrence);
      }),
    );
    const user = userEvent.setup();
    renderPage();

    const table = await desktopTable();
    await user.click(
      await table.findByRole("button", { name: `Editar ${rentRecurrence.description}` }),
    );

    const scopeDialog = await screen.findByRole("dialog");
    expect(within(scopeDialog).getByText("Qual parte da série editar?")).toBeInTheDocument();

    await user.click(within(scopeDialog).getByRole("radio", { name: /Só esta ocorrência/ }));
    await user.click(within(scopeDialog).getByRole("button", { name: "Continuar" }));

    // Scheduling fields are ignored server-side for THIS_ONE, so the form hides them.
    expect(await screen.findByText("Editar ocorrência")).toBeInTheDocument();
    expect(screen.queryByLabelText("Frequência")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Salvar" }));

    await waitFor(() => expect(updateCalls).toHaveLength(1));
    const params = new URL(updateCalls[0].url).searchParams;
    expect(params.get("scope")).toBe("THIS_ONE");
    expect(params.get("occurrenceDate")).toBeTruthy();
  });

  it("deletes a never-started series with a plain confirmation, no scope prompt", async () => {
    let deleted = false;
    server.use(
      http.delete("*/workspaces/:workspaceId/recurrences/:recurrenceId", () => {
        deleted = true;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    const table = await desktopTable();
    await user.click(
      await table.findByRole("button", { name: `Excluir ${futureSalaryRecurrence.description}` }),
    );

    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText("Excluir recorrência")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Excluir" }));

    await waitFor(() => expect(deleted).toBe(true));
  });

  it("asks the scope before deleting a series that may have materialized", async () => {
    let deleteUrl = "";
    server.use(
      http.delete("*/workspaces/:workspaceId/recurrences/:recurrenceId", ({ request }) => {
        deleteUrl = request.url;
        return new HttpResponse(null, { status: 204 });
      }),
    );
    const user = userEvent.setup();
    renderPage();

    const table = await desktopTable();
    await user.click(
      await table.findByRole("button", { name: `Excluir ${rentRecurrence.description}` }),
    );

    const scopeDialog = await screen.findByRole("dialog");
    expect(within(scopeDialog).getByText("Qual parte da série excluir?")).toBeInTheDocument();
    // Default choice is THIS_AND_FUTURE.
    await user.click(within(scopeDialog).getByRole("button", { name: "Excluir" }));

    await waitFor(() => expect(deleteUrl).not.toBe(""));
    expect(new URL(deleteUrl).searchParams.get("scope")).toBe("THIS_AND_FUTURE");
  });
});
