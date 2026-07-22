import { setupServer } from "msw/node";

import { handlers } from "@/mocks/handlers";

// Shared MSW server for tests (Vitest + jsdom). Wired into every test file
// via src/test/setup.ts, so FE-2/3/4 test files get request mocking for
// free and only need `server.use(...)` for scenario-specific overrides.
export const server = setupServer(...handlers);
