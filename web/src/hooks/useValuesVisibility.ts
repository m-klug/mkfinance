import { create } from "zustand";
import { persist } from "zustand/middleware";

// Global "hide values" toggle (eye icon) — masks every money amount on
// screen while using the app (shoulder-surfing privacy), persisted to
// localStorage the same way useTheme.ts persists the theme.

const STORAGE_KEY = "mk-finance-values-hidden";

interface ValuesVisibilityState {
  hidden: boolean;
  toggleHidden: () => void;
}

export const useValuesVisibilityStore = create<ValuesVisibilityState>()(
  persist(
    (set, get) => ({
      hidden: false,
      toggleHidden: () => set({ hidden: !get().hidden }),
    }),
    { name: STORAGE_KEY },
  ),
);

export function useValuesVisibility() {
  const hidden = useValuesVisibilityStore((state) => state.hidden);
  const toggleHidden = useValuesVisibilityStore((state) => state.toggleHidden);
  return { hidden, toggleHidden };
}
