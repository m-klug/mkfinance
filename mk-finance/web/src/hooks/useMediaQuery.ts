import { useSyncExternalStore } from "react";

/** SSR-safe media query hook, backed by useSyncExternalStore so it never desyncs from the DOM. */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const mql = window.matchMedia(query);
      mql.addEventListener("change", onChange);
      return () => mql.removeEventListener("change", onChange);
    },
    () => window.matchMedia(query).matches,
    () => false,
  );
}

// Tailwind's `sm` breakpoint (640px) — the same cutoff the dialog/bottom-sheet
// and every `max-sm:`/`sm:` responsive class in the app already use.
export function useIsMobile(): boolean {
  return useMediaQuery("(max-width: 639px)");
}
