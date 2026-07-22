import "@testing-library/jest-dom";
import "../i18n";
import { server } from "./server";

// jsdom does not implement matchMedia; useTheme() (src/hooks/useTheme.ts)
// reads it once to seed the initial light/dark preference. Stub it so any
// component that touches theme state doesn't crash in tests.
if (typeof window !== "undefined" && !window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
}

beforeAll(() => server.listen({ onUnhandledRequest: "bypass" }));

afterEach(() => {
  server.resetHandlers();
  localStorage.clear();
});

afterAll(() => server.close());
