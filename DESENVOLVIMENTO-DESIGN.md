# Reskin de UI — handoff `design_handoff_mk_finance/`

Branch `redesign-ui-mint` (a partir de `1f99979` em `main`... na verdade a partir do commit corrente de `aporte-sinal-e-pilar-conhecimento`). Fundação em `4f44df6`.

## Regra de ouro
**Só visual.** Nenhum hook, endpoint, cálculo ou regra de negócio muda. Alvo de pilar real continua vindo da API (30/10/20/10/25/5) — os números do mock são só dado de exemplo de tela, não mudam o seed/backend.

## Fundação já pronta (não mexer, é base compartilhada)
- `src/index.css` — tokens claro/escuro, Manrope self-hosted, radii/shadow/scrim.
- `src/components/ui/{button,card,dialog,badge,segmented-control,target-bar}.tsx`
- `src/components/layout/AppShell.tsx` — rail de navegação.
- `src/features/dashboard/dashboardFormat.ts` — `PILLAR_COLORS` já na paleta nova.

Use essas classes utilitárias novas em qualquer tela: `bg-page-bg bg-shell-bg bg-card bg-field bg-neutral bg-hero text-hero-foreground text-ink-primary text-ink-secondary text-ink-tertiary text-ink-label bg-success-bg text-success-ink bg-alert-bg text-alert-ink border-divider-card border-divider-row rounded-shell rounded-card rounded-modal rounded-field rounded-list-item rounded-pill`. Números monetários/% sempre com `className="tabular-nums"`. Ícones: lucide-react já instalado, mapear os glifos-placeholder do mock pro ícone lucide equivalente.

## Fichas (paralelas, cada agente dono só da própria pasta em `src/features/`)

Ler o próprio recorte em `~/Downloads/design_handoff_mk_finance/*.dc.html` (grep pelo título da tela — README tem a lista) + `renderVals()` do fim do arquivo pro formato de dado exato.

- **A — Dashboard + Auth**: `features/dashboard/**`, `features/auth/**`. Telas 01, 04 (claro×escuro referência), 13, dashboard mobile em 03. Painel-herói escuro de alocação por pilar é o elemento mais visível — usar `TargetBar`.
- **B — Lançamentos**: `features/transactions/**`. Telas 02, 03 (modal + variação cartão). `QuickTransactionDialog` ganha o preview "impacto no pilar" com `TargetBar`.
- **C — Contas/Caixinhas/Cartões/Faturas**: `features/accounts/**`, `features/vaults/**`, `features/cards/**`. Telas 05, 06, 07, 08.
- **D — Categorias/Recorrências/Calendário/Config**: `features/categories/**`, `features/recurrences/**`, `features/calendar/**`, `features/settings/**`. Telas 09, 10, 11, 12.

Todo modal de criar/editar vira modal central (já é o padrão do `Dialog` da fundação — só ajustar conteúdo/campos pro layout novo).

## Gate por ficha
```bash
cd mk-finance/web && npm run build && npm test
```
Teste que quebrar por causa de texto visível virando ícone (como o próprio `App.test.tsx` da fundação) é esperado — corrige a asserção pro papel/aria-label, não reverte o visual.

## Integração
Merge sequencial das 4 branches em `redesign-ui-mint`, rebuild+test a cada merge (3-way pode deixar import órfão que só `tsc` estrito pega — lição já registrada no projeto).
