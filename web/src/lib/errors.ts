import type { TFunction } from "i18next";

import { ApiError } from "@/lib/api/fetcher";
import { formatCents } from "@/lib/format";

// Every mutation/query that surfaces an ApiError should go through this to
// turn the machine-readable `code` (PLANEJAMENTO §3.3) into pt-BR copy —
// falls back to error.UNKNOWN_ERROR for codes without a translation key yet.
export function translateApiError(t: TFunction, error: unknown): string {
  if (error instanceof ApiError) {
    const key = `error.${error.body.code}`;
    const translated = t(key, interpolationParams(error.body.params));
    return translated === key ? t("error.UNKNOWN_ERROR") : translated;
  }
  return t("error.UNKNOWN_ERROR");
}

/**
 * Money-shaped params (`shortfallCents`, `balanceCents`, `remainingCents`, `pendingCents`, `linkedCents` — VAULT_INSUFFICIENT_BALANCE and
 * VAULT_NOT_EMPTY, CT-2/AppException) arrive as raw integer cents, same as everywhere else in the
 * API. i18next interpolation only ever inserts what it is handed, so this formats the ones a
 * message actually needs into BRL before the translation runs — every other param rides through
 * unformatted for codes that reference it as-is (e.g. `{{name}}`, `{{email}}`).
 */
function interpolationParams(
  params: Record<string, unknown> | undefined,
): Record<string, unknown> | undefined {
  if (!params) return undefined;
  const next: Record<string, unknown> = { ...params };
  if (typeof params.shortfallCents === "number") {
    next.shortfallAmount = formatCents(params.shortfallCents);
  }
  if (typeof params.balanceCents === "number") {
    next.balanceAmount = formatCents(params.balanceCents);
  }
  if (typeof params.remainingCents === "number") {
    next.remainingAmount = formatCents(params.remainingCents);
  }
  if (typeof params.pendingCents === "number") {
    next.pendingAmount = formatCents(params.pendingCents);
  }
  if (typeof params.linkedCents === "number") {
    next.linkedAmount = formatCents(params.linkedCents);
  }
  if (typeof params.principalCents === "number") {
    next.principalAmount = formatCents(params.principalCents);
  }
  return next;
}
