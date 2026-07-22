import { Navigate, Outlet } from "react-router";
import { useTranslation } from "react-i18next";

import { useGetCurrentUser } from "@/lib/api/generated/auth/auth";

// Gate for every route inside the shell (App.tsx): fetches the current user
// (auth `me`) and redirects to /login while unauthenticated (PLANEJAMENTO
// §6.1). Rendered as a layout route so protected pages just declare
// themselves as children — no per-page guard logic needed.
export function RequireAuth() {
  const { t } = useTranslation();
  const { data, isPending, isError } = useGetCurrentUser({
    query: { retry: false },
  });

  if (isPending) {
    return (
      <div className="flex min-h-screen items-center justify-center text-muted-foreground">
        {t("common.loading")}
      </div>
    );
  }

  if (isError || !data) {
    return <Navigate to="/login" replace />;
  }

  return <Outlet />;
}
