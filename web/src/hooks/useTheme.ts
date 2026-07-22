import { create } from "zustand";
import { persist } from "zustand/middleware";

// Light/dark theme, persisted to localStorage and initialized from the OS
// preference (prefers-color-scheme) the first time the app runs, per FE-1
// scope (PLANEJAMENTO §6.7 "tema claro/escuro"). Backed by zustand's persist
// middleware so the value survives reloads without extra plumbing.

export type Theme = "light" | "dark";

const STORAGE_KEY = "mk-finance-theme";

function getPreferredTheme(): Theme {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") {
    return "light";
  }
  try {
    return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
  } catch {
    return "light";
  }
}

function applyThemeClass(theme: Theme) {
  if (typeof document === "undefined") return;
  document.documentElement.classList.toggle("dark", theme === "dark");
}

interface ThemeState {
  theme: Theme;
  setTheme: (theme: Theme) => void;
  toggleTheme: () => void;
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set, get) => ({
      theme: getPreferredTheme(),
      setTheme: (theme) => {
        applyThemeClass(theme);
        set({ theme });
      },
      toggleTheme: () => {
        const next: Theme = get().theme === "dark" ? "light" : "dark";
        applyThemeClass(next);
        set({ theme: next });
      },
    }),
    {
      name: STORAGE_KEY,
      onRehydrateStorage: () => (state) => {
        if (state) applyThemeClass(state.theme);
      },
    },
  ),
);

// Apply the initial theme class as soon as the module loads — covers the
// case where nothing was persisted yet, so the very first paint already
// matches prefers-color-scheme instead of waiting for a React effect.
applyThemeClass(useThemeStore.getState().theme);

export function useTheme() {
  const theme = useThemeStore((state) => state.theme);
  const setTheme = useThemeStore((state) => state.setTheme);
  const toggleTheme = useThemeStore((state) => state.toggleTheme);
  return { theme, setTheme, toggleTheme };
}
