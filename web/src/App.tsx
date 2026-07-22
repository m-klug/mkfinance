import { Navigate, Route, Routes } from "react-router";
import { useTranslation } from "react-i18next";

import { Toaster } from "@/components/ui/sonner";
import { RequireAuth } from "@/components/auth/RequireAuth";
import { AppShell } from "@/components/layout/AppShell";
import LoginPage from "@/features/auth/LoginPage";
import RegisterPage from "@/features/auth/RegisterPage";

// Feature screens land in FE-2/3/4; kept as translated placeholders here so
// the shell and routing are fully navigable from E1 onward.
function Placeholder({ titleKey }: { titleKey: string }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-1 flex-col items-center justify-center gap-2 p-8 text-center">
      <h1 className="text-2xl font-semibold">{t(titleKey)}</h1>
      <p className="text-muted-foreground">{t("placeholder.underConstruction")}</p>
    </div>
  );
}

// No <BrowserRouter> here on purpose — it lives in main.tsx so tests can
// swap in a <MemoryRouter> (see src/test/renderWithProviders.tsx) and drive
// App at any initial route without touching window.location.
export default function App() {
  return (
    <>
      <Toaster />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route element={<RequireAuth />}>
          <Route element={<AppShell />}>
            <Route path="/" element={<Placeholder titleKey="nav.dashboard" />} />
            <Route path="/transactions" element={<Placeholder titleKey="nav.transactions" />} />
            <Route path="/accounts" element={<Placeholder titleKey="nav.accounts" />} />
            <Route path="/cards" element={<Placeholder titleKey="nav.cards" />} />
            <Route path="/categories" element={<Placeholder titleKey="nav.categories" />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
