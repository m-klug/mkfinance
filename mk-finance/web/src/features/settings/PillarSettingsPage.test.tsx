import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";

import PillarSettingsPage from "@/features/settings/PillarSettingsPage";
import { Toaster } from "@/components/ui/sonner";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import { defaultPillarAllocations, pillarAllocationReadHandler } from "@/features/settings/pillarFixtures";
import type { PillarAllocationsRequest } from "@/lib/api/generated/mKFinanceAPI.schemas";

// The page itself renders no <Toaster/> (that lives once in App.tsx), so
// mount it alongside the page here to assert on the success/error copy.
function renderPage() {
  seedAuthenticatedSession();
  return renderWithProviders(
    <>
      <Toaster />
      <PillarSettingsPage />
    </>,
    { route: "/settings/pillars" },
  );
}

describe("PillarSettingsPage", () => {
  beforeEach(() => {
    server.use(pillarAllocationReadHandler());
  });

  it("shows the targets currently in force, summing to 100", async () => {
    renderPage();

    expect(await screen.findByLabelText("Custos Fixos")).toHaveValue(30);
    expect(screen.getByLabelText("Conforto")).toHaveValue(10);
    expect(screen.getByLabelText("Metas")).toHaveValue(20);
    expect(screen.getByLabelText("Prazeres")).toHaveValue(10);
    expect(screen.getByLabelText("Liberdade Financeira")).toHaveValue(25);
    expect(screen.getByLabelText("Conhecimento")).toHaveValue(5);

    expect(screen.getByText("Soma: 100%")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Salvar" })).toBeEnabled();
  });

  it("blocks saving and explains why when the sum is not 100", async () => {
    const putCalls: PillarAllocationsRequest[] = [];
    server.use(
      http.put("*/workspaces/:workspaceId/pillar-allocations", async ({ request }) => {
        putCalls.push((await request.json()) as PillarAllocationsRequest);
        return HttpResponse.json(defaultPillarAllocations);
      }),
    );
    const user = userEvent.setup();
    renderPage();

    const comfort = await screen.findByLabelText("Conforto");
    await user.clear(comfort);
    await user.type(comfort, "15");

    expect(await screen.findByText("Soma: 105%")).toBeInTheDocument();
    expect(screen.getByText("A soma dos seis pilares precisa ser 100%.")).toBeInTheDocument();

    const saveButton = screen.getByRole("button", { name: "Salvar" });
    expect(saveButton).toBeDisabled();

    expect(putCalls).toHaveLength(0);
  });

  it("saves a valid edit with the six pillars via PUT", async () => {
    const putCalls: PillarAllocationsRequest[] = [];
    server.use(
      http.put("*/workspaces/:workspaceId/pillar-allocations", async ({ request }) => {
        const body = (await request.json()) as PillarAllocationsRequest;
        putCalls.push(body);
        return HttpResponse.json(
          body.allocations.map((allocation) => ({ ...allocation, effectiveFrom: "2026-07-01" })),
        );
      }),
    );
    const user = userEvent.setup();
    renderPage();

    // Move 5 points from Comfort to Goals — the sum stays 100.
    const comfort = await screen.findByLabelText("Conforto");
    await user.clear(comfort);
    await user.type(comfort, "5");

    const goals = screen.getByLabelText("Metas");
    await user.clear(goals);
    await user.type(goals, "25");

    expect(await screen.findByText("Soma: 100%")).toBeInTheDocument();
    const saveButton = screen.getByRole("button", { name: "Salvar" });
    expect(saveButton).toBeEnabled();

    await user.click(saveButton);

    await waitFor(() => expect(putCalls).toHaveLength(1));
    expect(putCalls[0].allocations).toHaveLength(6);
    expect(putCalls[0].allocations.reduce((sum, item) => sum + item.targetPercent, 0)).toBe(100);
    expect(putCalls[0].allocations).toEqual(
      expect.arrayContaining([
        { pillar: "COMFORT", targetPercent: 5 },
        { pillar: "GOALS", targetPercent: 25 },
      ]),
    );

    expect(await screen.findByText("Percentuais-alvo atualizados.")).toBeInTheDocument();
  });

  it("translates the server's sum-must-be-100 rejection", async () => {
    server.use(
      http.put("*/workspaces/:workspaceId/pillar-allocations", () =>
        HttpResponse.json({ code: "PILLAR_TARGETS_MUST_SUM_100" }, { status: 400 }),
      ),
    );
    const user = userEvent.setup();
    renderPage();

    await screen.findByLabelText("Custos Fixos");
    await user.click(screen.getByRole("button", { name: "Salvar" }));

    expect(
      await screen.findByText("A soma dos seis pilares precisa ser 100%."),
    ).toBeInTheDocument();
  });
});
