import { Route, Routes } from "react-router-dom"
import { AppShell } from "@/components/layout/AppShell"
import { Toaster } from "@/components/ui/sonner"
import { Dashboard } from "@/pages/Dashboard"
import { NovaOrdem } from "@/pages/NovaOrdem"
import { Historico } from "@/pages/Historico"
import { Apuracao } from "@/pages/Apuracao"

function App() {
  return (
    <>
      <Routes>
        <Route element={<AppShell />}>
          <Route path="/" element={<Dashboard />} />
          <Route path="/lancamento" element={<NovaOrdem />} />
          <Route path="/historico" element={<Historico />} />
          <Route path="/apuracao" element={<Apuracao />} />
        </Route>
      </Routes>
      <Toaster />
    </>
  )
}

export default App
