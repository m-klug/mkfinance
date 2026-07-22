// Money is integer cents end to end (PLANEJAMENTO §5 R1) — these helpers are
// the only place where the user-facing string and the wire value meet.

/**
 * Reads whatever the user typed as a stream of digits interpreted as cents:
 * "12345" and "R$ 123,45" both mean 12345 cents. This is the pattern used by
 * Brazilian banking apps (the amount grows from the right), which keeps the
 * quick-entry modal at a single keystroke sequence with no separator typing.
 */
export function parseAmountToCents(raw: string): number {
  const digits = raw.replace(/\D/g, "").slice(0, 15);
  return digits ? Number(digits) : 0;
}
