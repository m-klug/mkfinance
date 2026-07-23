import { expect, test, type Page } from "@playwright/test";

// End-to-end smoke of the canonical happy path against the REAL stack (backend
// + Postgres, no MSW): register -> create account -> income entry -> create
// card -> installment card purchase -> pay the statement -> dashboard reflects
// it. This is the one flow that MSW never exercised, so it is where the real
// context-path + CSRF wiring (INT-1 Phase 2) is proven from the browser.
//
// Prerequisites: the API and Postgres must be running (docker compose up, or a
// local spring-boot:run against a Postgres on 5432). The Vite dev server is
// started by playwright.config.ts (VITE_API_MOCK=false).

// R$ 5.000,00 etc. — the money field reads keystrokes as cents (money.ts), so
// "500000" means R$ 5.000,00.
const SALARY = "500000";
const CARD_LIMIT = "1000000"; // R$ 10.000,00
const PURCHASE_TOTAL = "150000"; // R$ 1.500,00, split in 3x

function uniqueEmail(): string {
  return `smoke-${Date.now()}-${Math.floor(Math.random() * 1e6)}@mkfinance.test`;
}

async function fillMoney(page: Page, id: string, digits: string) {
  const field = page.locator(`#${id}`);
  await field.click();
  await field.fill("");
  await field.type(digits);
}

test("register through dashboard: the canonical happy path", async ({ page }) => {
  const email = uniqueEmail();

  // --- Register (first mutation: CSRF must already be primed) ---
  await page.goto("/register");
  await page.locator("#register-name").fill("Smoke Tester");
  await page.locator("#register-email").fill(email);
  await page.locator("#register-password").fill("password123");
  await page.getByRole("button", { name: "Criar conta", exact: true }).click();

  // Lands on the dashboard shell.
  await expect(page.getByRole("heading", { name: "Dashboard", level: 1 })).toBeVisible();
  await expect(page.getByText("Alocação por pilar")).toBeVisible();

  // --- Create a checking account ---
  await page.getByRole("link", { name: "Contas" }).click();
  await page.getByRole("button", { name: "Nova conta" }).click();
  await page.locator("#account-name").fill("Conta Corrente");
  await page.locator("#account-type").selectOption("CHECKING");
  const accountDialog = page.getByRole("dialog");
  await accountDialog.getByRole("button", { name: "Salvar" }).click();
  await expect(page.getByRole("cell", { name: "Conta Corrente", exact: true })).toBeVisible();

  // --- Income entry (salary) via quick entry ---
  await page.getByRole("button", { name: "Novo lançamento" }).click();
  let dialog = page.getByRole("dialog");
  await dialog.getByRole("tab", { name: "Receita" }).click();
  await fillMoney(page, "quick-amount", SALARY);
  await page.locator("#quick-description").fill("Salário");
  // First real category option (index 0 is the placeholder).
  await page.locator("#quick-category").selectOption({ index: 1 });
  await dialog.getByRole("button", { name: "Salvar" }).click();
  await expect(dialog).toBeHidden();

  // --- Create a credit card (closing day high enough that today's purchase
  // lands in the current-month statement the page opens on) ---
  await page.getByRole("link", { name: "Cartões" }).click();
  await page.getByRole("button", { name: "Novo cartão" }).click();
  await page.locator("#card-name").fill("Cartão Principal");
  await fillMoney(page, "card-limit", CARD_LIMIT);
  await page.locator("#card-closing-day").selectOption("28");
  await page.locator("#card-due-day").selectOption("5");
  const cardDialog = page.getByRole("dialog");
  await cardDialog.getByRole("button", { name: "Salvar" }).click();
  await expect(page.getByRole("cell", { name: "Cartão Principal", exact: true })).toBeVisible();

  // --- Installment card purchase (3x) via quick entry ---
  await page.getByRole("button", { name: "Novo lançamento" }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByRole("tab", { name: "Cartão" }).click();
  await fillMoney(page, "quick-amount", PURCHASE_TOTAL);
  await page.locator("#quick-description").fill("Notebook");
  await page.locator("#quick-category").selectOption({ index: 1 });
  await page.locator("#quick-installments").selectOption("3");
  await dialog.getByRole("button", { name: "Salvar" }).click();
  await expect(dialog).toBeHidden();

  // --- Open the card statement and pay it in full ---
  await page.getByRole("link", { name: /fatura/i }).first().click();
  // The first installment (1/3 = R$ 500,00) shows up in this month's statement.
  await expect(page.getByRole("cell", { name: "Notebook" }).first()).toBeVisible();
  await expect(page.getByText("1/3")).toBeVisible();

  await page.getByRole("button", { name: "Pagar fatura" }).click();
  const payDialog = page.getByRole("dialog");
  await payDialog.locator("#payment-account").selectOption({ label: "Conta Corrente" });
  await payDialog.getByRole("button", { name: "Pagar", exact: true }).click();
  await expect(payDialog).toBeHidden();
  // Payment recorded — the payments section now lists it.
  await expect(page.getByText("Pagamentos")).toBeVisible();

  // --- Dashboard confirms the movement ---
  // Full load (not a client-side nav) so the summary/allocation queries fetch
  // fresh server truth rather than the staleTime-cached empty snapshot from the
  // immediate post-register render.
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Dashboard", level: 1 })).toBeVisible();
  await expect(page.getByText("Alocação por pilar")).toBeVisible();
  // Income was registered, so the allocation panel is NOT in its no-income
  // state (that warning only shows when the month has zero income).
  await expect(
    page.getByText("Sem receita registrada neste mês", { exact: false }),
  ).toHaveCount(0);
  // The statement payment created an expense on the checking account, so the
  // balance panel renders the account.
  await expect(page.getByText("Conta Corrente").first()).toBeVisible();
});
