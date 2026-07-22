import { setupWorker } from "msw/browser";

import { handlers } from "./handlers";

// Started from main.tsx while VITE_API_MOCK=true (see .env.development).
// See ./handlers.ts for what each tag returns and why auth is stateful.
export const worker = setupWorker(...handlers);
