import { defineConfig } from "orval";

// Generates the typed API client (TanStack Query hooks) and MSW mocks from the
// contract. The contract is the single sync point between front and back —
// regenerate with `npm run api:generate` whenever it changes. Generated code
// is never edited by hand.
export default defineConfig({
  mkFinance: {
    input: "../contracts/openapi.yaml",
    output: {
      mode: "tags-split",
      target: "src/lib/api/generated",
      client: "react-query",
      mock: true,
      clean: true,
      override: {
        mutator: {
          path: "src/lib/api/fetcher.ts",
          name: "apiFetch",
        },
      },
    },
  },
});
