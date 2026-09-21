import { useCallback } from "react";

import { formatCents, HIDDEN_VALUE_MASK } from "@/lib/format";
import { useValuesVisibility } from "@/hooks/useValuesVisibility";

/**
 * Drop-in replacement for formatCents() that respects the global
 * "hide values" toggle — returns the masked placeholder instead of the
 * formatted amount whenever values are hidden.
 */
export function useFormatCents() {
  const { hidden } = useValuesVisibility();
  return useCallback((cents: number) => (hidden ? HIDDEN_VALUE_MASK : formatCents(cents)), [hidden]);
}
