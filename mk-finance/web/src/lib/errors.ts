import type { TFunction } from "i18next";

import { ApiError } from "@/lib/api/fetcher";

// Every mutation/query that surfaces an ApiError should go through this to
// turn the machine-readable `code` (PLANEJAMENTO §3.3) into pt-BR copy —
// falls back to error.UNKNOWN_ERROR for codes without a translation key yet.
export function translateApiError(t: TFunction, error: unknown): string {
  if (error instanceof ApiError) {
    const key = `error.${error.body.code}`;
    const translated = t(key);
    return translated === key ? t("error.UNKNOWN_ERROR") : translated;
  }
  return t("error.UNKNOWN_ERROR");
}
