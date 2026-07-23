import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router";
import "./i18n";
import "./index.css";
import App from "./App";
import { API_BASE_URL } from "./lib/api/fetcher";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
});

async function enableMocking() {
  // MSW serves the contract mocks until INT-1 flips this off (VITE_API_MOCK).
  if (import.meta.env.VITE_API_MOCK !== "true") {
    return;
  }
  const { worker } = await import("./mocks/browser");
  return worker.start({ onUnhandledRequest: "bypass" });
}

// Primes the CSRF cookie against the real backend. The login/register pages issue no GET before
// their POST (RequireAuth's /auth/me only runs inside the shell), so without this the very first
// mutation would arrive with no XSRF-TOKEN cookie and be rejected (403). A harmless GET /auth/me
// makes the server issue the readable token cookie; the 401 body is ignored. Skipped under MSW.
async function primeCsrfToken() {
  if (import.meta.env.VITE_API_MOCK === "true") {
    return;
  }
  try {
    await fetch(`${API_BASE_URL}/auth/me`, { credentials: "include" });
  } catch {
    // Backend unreachable at boot — the auth flow will surface the error on the first real call.
  }
}

void Promise.all([enableMocking(), primeCsrfToken()]).then(() => {
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </QueryClientProvider>
    </StrictMode>,
  );
});
