import { Navigate, Route, Routes } from "react-router";

import { Toaster } from "@/components/ui/sonner";
import { RequireAuth } from "@/components/auth/RequireAuth";
import { AppShell } from "@/components/layout/AppShell";
import LoginPage from "@/features/auth/LoginPage";
import RegisterPage from "@/features/auth/RegisterPage";
import AccountsPage from "@/features/accounts/AccountsPage";
import VaultsPage from "@/features/vaults/VaultsPage";
import VaultsOverviewPage from "@/features/vaults/VaultsOverviewPage";
import MorePage from "@/features/more/MorePage";
import CardsPage from "@/features/cards/CardsPage";
import CardStatementsPage from "@/features/cards/CardStatementsPage";
import CategoriesPage from "@/features/categories/CategoriesPage";
import TransactionsPage from "@/features/transactions/TransactionsPage";
import DashboardPage from "@/features/dashboard/DashboardPage";
import PillarSettingsPage from "@/features/settings/PillarSettingsPage";
import RecurrencesPage from "@/features/recurrences/RecurrencesPage";
import CalendarPage from "@/features/calendar/CalendarPage";
import SimulatorPage from "@/features/simulator/SimulatorPage";
import InvestmentsPage from "@/features/investments/InvestmentsPage";

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
            <Route path="/" element={<DashboardPage />} />
            <Route path="/transactions" element={<TransactionsPage />} />
            <Route path="/accounts" element={<AccountsPage />} />
            <Route path="/accounts/:accountId/vaults" element={<VaultsPage />} />
            <Route path="/vaults" element={<VaultsOverviewPage />} />
            <Route path="/more" element={<MorePage />} />
            <Route path="/cards" element={<CardsPage />} />
            <Route path="/cards/:cardId/statements" element={<CardStatementsPage />} />
            <Route path="/categories" element={<CategoriesPage />} />
            <Route path="/recurrences" element={<RecurrencesPage />} />
            <Route path="/calendar" element={<CalendarPage />} />
            <Route path="/investments" element={<InvestmentsPage />} />
            <Route path="/simulator" element={<SimulatorPage />} />
            <Route path="/settings/pillars" element={<PillarSettingsPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
