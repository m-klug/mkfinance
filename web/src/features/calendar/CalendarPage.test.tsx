import { screen, within } from "@testing-library/react";

import CalendarPage from "@/features/calendar/CalendarPage";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import {
  calendarReadHandlers,
  rentPendingEntry,
  salaryPendingEntry,
  statementDueEntry,
} from "@/features/calendar/calendarFixtures";

function renderPage() {
  seedAuthenticatedSession();
  return renderWithProviders(<CalendarPage />, { route: "/calendar" });
}

describe("CalendarPage", () => {
  beforeEach(() => {
    server.use(...calendarReadHandlers());
  });

  it("lists every entry of the month, grouped by date", async () => {
    renderPage();

    expect(await screen.findByText(rentPendingEntry.description)).toBeInTheDocument();
    expect(screen.getByText(salaryPendingEntry.description)).toBeInTheDocument();
    expect(screen.getByText(statementDueEntry.description)).toBeInTheDocument();
    expect(screen.getAllByText("Vencimento de fatura")).toHaveLength(1);
    expect(screen.getAllByText("Lançamento pendente")).toHaveLength(2);
  });

  it("badges the occurrence that came from a recurrence, but not a plain pending transaction", async () => {
    renderPage();

    const rentRow = (await screen.findByText(rentPendingEntry.description)).closest("li") as HTMLElement;
    expect(within(rentRow).getByText("Recorrência")).toBeInTheDocument();

    const salaryRow = (await screen.findByText(salaryPendingEntry.description)).closest(
      "li",
    ) as HTMLElement;
    expect(within(salaryRow).queryByText("Recorrência")).not.toBeInTheDocument();
  });

  it("shows an outgoing statement due and an incoming pending transaction with opposite signs", async () => {
    renderPage();

    const dueRow = (await screen.findByText(statementDueEntry.description)).closest("li") as HTMLElement;
    expect(within(dueRow).getByText(/-.*1\.200,00/)).toBeInTheDocument();

    const salaryRow = (await screen.findByText(salaryPendingEntry.description)).closest(
      "li",
    ) as HTMLElement;
    expect(within(salaryRow).getByText(/5\.000,00/)).toBeInTheDocument();
    expect(within(salaryRow).queryByText(/-.*5\.000,00/)).not.toBeInTheDocument();
  });

  it("shows the empty state when the month has no entries", async () => {
    server.use(...calendarReadHandlers({ entries: [] }));
    renderPage();

    expect(await screen.findByText("Nenhum evento neste mês.")).toBeInTheDocument();
  });
});
