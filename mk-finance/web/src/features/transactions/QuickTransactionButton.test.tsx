import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import App from "@/App";
import { renderWithProviders } from "@/test/renderWithProviders";
import { seedAuthenticatedSession } from "@/test/authFixtures";
import { server } from "@/test/server";
import { financeReadHandlers } from "@/test/financeFixtures";

// PLANEJAMENTO §6.3 requires quick entry to be reachable from anywhere, so it
// is exercised through the real shell rather than in isolation. (/transactions
// is left out on purpose: that screen has its own button with the same label.)
describe("quick entry from the app shell", () => {
  beforeEach(() => {
    server.use(...financeReadHandlers());
  });

  it.each(["/", "/accounts", "/categories"])(
    "opens the quick entry modal from %s",
    async (route) => {
      const user = userEvent.setup();
      seedAuthenticatedSession();
      renderWithProviders(<App />, { route });

      // Two triggers exist for responsive layouts (desktop topbar pill, mobile
      // tab bar FAB) — the topbar one lives in the <header> banner landmark.
      const header = await screen.findByRole("banner");
      await user.click(within(header).getByRole("button", { name: "Novo lançamento" }));

      const dialog = await screen.findByRole("dialog");
      expect(within(dialog).getByRole("tab", { name: "Aporte" })).toBeInTheDocument();
      expect(within(dialog).getByLabelText("Valor")).toBeInTheDocument();
    },
  );
});
