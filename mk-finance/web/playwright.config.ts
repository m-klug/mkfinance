import { defineConfig, devices } from "@playwright/test";

// Smoke E2E for INT-1: drives the real SPA against the real backend + Postgres
// (no MSW). The dev server is started here (reusing an already-running one);
// the API + database must be up separately — either the docker compose stack
// or a local `spring-boot:run` with a Postgres on 5432. See e2e/README.md.
export default defineConfig({
  testDir: "./e2e",
  testMatch: "**/*.spec.ts",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  timeout: 30_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:5173",
    trace: "on-first-retry",
  },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
  ],
  webServer: {
    command: "npm run dev",
    url: "http://localhost:5173",
    reuseExistingServer: true,
    timeout: 60_000,
  },
});
