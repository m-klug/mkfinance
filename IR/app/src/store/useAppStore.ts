import { create } from "zustand"
import { ATIVOS, DATA_REFERENCIA_PADRAO, ORDENS_SEED } from "@/lib/mockData"
import type { Ativo, Ordem } from "@/lib/types"

interface AppState {
  ativos: Ativo[]
  ordens: Ordem[]
  dataReferencia: string
  ativoSelecionadoDashboard: string | null
  addOrdem: (ordem: Omit<Ordem, "id">) => Ordem
  setDataReferencia: (data: string) => void
  setAtivoSelecionadoDashboard: (ticker: string | null) => void
  getAtivo: (ticker: string) => Ativo | undefined
}

let counter = 1000

export const useAppStore = create<AppState>((set, get) => ({
  ativos: ATIVOS,
  ordens: ORDENS_SEED,
  dataReferencia: DATA_REFERENCIA_PADRAO,
  ativoSelecionadoDashboard: null,
  addOrdem: (ordem) => {
    const nova: Ordem = { ...ordem, id: `u${counter++}` }
    set((s) => ({ ordens: [...s.ordens, nova] }))
    return nova
  },
  setDataReferencia: (data) => set({ dataReferencia: data }),
  setAtivoSelecionadoDashboard: (ticker) => set({ ativoSelecionadoDashboard: ticker }),
  getAtivo: (ticker) => get().ativos.find((a) => a.ticker === ticker),
}))
