# Plano de Desenvolvimento do MVP — MK Finance

> Plano operacional para execução **multi-agente** do MVP definido em `PLANEJAMENTO.md`.
> Nome: **MK Finance** (pasta `mk-finance/`, pacote `br.com.mkfinance`, infra `mkfinance`).
> Gerado em 19/07/2026. Estado: **E0 concluída** (fundação criada nesta sessão).

---

## 1. Como executar este plano com agentes

### 1.1 Personas (skills em `.claude/agents/`)

| Persona | Skill | Responsabilidade |
|---|---|---|
| `java-architect` | SKILL/java-architect.md | Contrato OpenAPI, schema, decisões de arquitetura e o domínio mais complexo (motor de fatura/parcelas — BE-4) |
| `spring-boot-engineer` | SKILL/spring-boot-engineer.md | Features de backend (BE-1, BE-2, BE-3, BE-5) |
| `react-specialist` | SKILL/react-specialist.md | Todo o frontend (FE-1 a FE-4) |

Cada workstream (ficha na seção 4) é executado por **um agente**, com o prompt contendo: a ficha completa, as convenções da seção 2 e a instrução de ler `PLANEJAMENTO.md` + `mk-finance/contracts/openapi.yaml` antes de codar.

### 1.2 Regras de sincronização (o que evita conflito entre agentes)

1. **O contrato é o ponto único de sincronização**: `mk-finance/contracts/openapi.yaml` define a API. O front **não espera o back** — gera cliente tipado + mocks MSW a partir do contrato (`npm run api:generate`) e desenvolve contra os mocks. A integração real acontece só em INT-1.
2. **Mudança de contrato é tarefa exclusiva do `java-architect`**: nenhum agente BE ou FE edita `openapi.yaml`. Se um workstream precisa de mudança no contrato, ele PARA, registra a necessidade, e uma tarefa de contrato é executada antes de continuar (as demais frentes não são afetadas).
3. **Propriedade exclusiva de pastas** — um agente nunca edita pasta de outro:
   - Backend: cada BE-x é dono dos seus pacotes de feature (listados na ficha). Pacotes compartilhados (`infrastructure/`, `api/error/`, config de segurança) pertencem a BE-1 depois de E1; alterações neles por outros workstreams viram pedido, não edição direta.
   - Frontend: FE-x é dono de `web/src/features/<área>/`. `web/src/components/ui/`, `web/src/lib/` e `web/src/i18n/` pertencem a FE-1 depois de E1.
   - Migrações Flyway: **append-only** — cada workstream adiciona `V<n>__<slug>.sql` novo, nunca edita migração existente. Numeração reservada por ficha (evita colisão de versão entre agentes paralelos).
4. **Definition of Done de toda tarefa**: código + testes passando localmente (`./mvnw verify` no back — requer Docker para Testcontainers; `npm test && npm run build` no front) + chaves i18n adicionadas (front) + zero warning novo de compilação.
5. **Sem git nesta fase** (decisão do usuário): o "checkpoint" entre etapas é o gate de verificação da seção 5. Recomenda-se fortemente ativar git antes de E2 — está registrado como pendência.

### 1.3 Ordem de execução e paralelismo

```
E0 Fundação (feita) ─┬─ E1: BE-1 ─┬─ E2: BE-3 ─┬─ E3: BE-5 ─┬─ E4/INT-1
                     ├─ E1: BE-2 ─┤   BE-4 ────┤   FE-3 ────┤   (sequencial)
                     └─ E1: FE-1 ─┘   FE-2 ────┘   FE-4 ────┘
```

Dentro de cada etapa, as frentes rodam **em paralelo** (agentes simultâneos). Uma etapa só começa quando o gate da anterior passou (seção 5). Dependências pontuais estão nas fichas.

---

## 2. Convenções obrigatórias (todas as fichas herdam)

- **Fonte da verdade de negócio**: `PLANEJAMENTO.md` — em especial seção 3.3 (código em inglês + glossário PT→EN, i18n), seção 4 (modelo de dados) e seção 5 (regras de negócio 1–10). Em conflito entre este documento e o PLANEJAMENTO, o PLANEJAMENTO vence e a divergência é registrada.
- **Dinheiro**: sempre `long` em **centavos** (`amount_cents BIGINT`). Nunca float/double/BigDecimal para valores monetários em domínio. Divisão de parcelas: resto na última parcela (regra 1).
- **Datas de negócio**: `LocalDate` (coluna `DATE`), timezone de referência `America/Sao_Paulo` (regra 7). `TIMESTAMPTZ` apenas para auditoria (`created_at`/`updated_at`).
- **Isolamento por workspace** (regra 8): toda query/endpoint é escopada por `workspace_id`; a autorização de membership é resolvida uma vez por request (interceptor/filter de BE-1) — nunca reimplementada ad hoc.
- **Erros da API**: código de máquina + parâmetros (ex.: `STATEMENT_ALREADY_PAID`), estrutura `ErrorResponse` do contrato; o front traduz. Backend não retorna mensagem em português.
- **i18n no front**: nenhuma string literal em JSX — tudo via `t('chave')` com `pt-BR` em `web/src/i18n/locales/pt-BR/`.
- **Testes**: back = JUnit 5 + Testcontainers (Postgres real; base pronta em `api/src/test`); front = Vitest + React Testing Library. Regras de negócio (seção 5) exigem testes de unidade dedicados por regra.
- **Layout backend** (espelha o app IR): `domain/` (model, service puro, exception), `application/` (services transacionais), `api/` (controller, dto, mapper, error), `infrastructure/` (config, integrações).

---

## 3. Etapas

| Etapa | Frentes (paralelas) | Persona | Pré-requisito |
|---|---|---|---|
| **E0 — Fundação** ✅ | esqueleto api/web/docker, contrato v0, schema V1/V2 | (feita nesta sessão) | — |
| **E1 — Base** ✅ | BE-1 Auth & Workspaces · BE-2 Contas & Categorias & Pilares · FE-1 Fundação visual | spring-boot-engineer, react-specialist | Gate E0 |
| **E2 — Núcleo** | BE-3 Transações & Aportes · BE-4 Cartão de crédito · FE-2 Telas de contas/categorias/extrato | spring-boot-engineer, java-architect, react-specialist | Gate E1 |
| **E3 — Visão** | BE-5 Dashboard & Alocação + CSV · FE-3 Telas de cartão/fatura · FE-4 Dashboard | spring-boot-engineer, react-specialist ×2 | Gate E2 |
| **E4 — Integração** | INT-1 (sequencial, um agente) | java-architect | Gate E3 |

Reserva de versões Flyway (append-only): BE-1 → V3–V4 · BE-2 → V5–V6 · BE-3 → V7–V8 · BE-4 → V9–V11 · BE-5 → V12. (V1 schema e V2 seed já existem; se um workstream não usar sua reserva, os números ficam vagos — sem renumeração.)

---

## 4. Fichas de tarefa

### BE-1 — Autenticação & Workspaces `[E1 · spring-boot-engineer]`

- **Objetivo**: registro/login/logout com sessão via cookie httpOnly (Spring Security), workspace pessoal criado no registro, convite de membro por link, e o **mecanismo central de isolamento por workspace** usado por todos os demais workstreams.
- **Entradas**: PLANEJAMENTO §6.1, §5 regra 8; contrato: tags `auth` e `workspaces`.
- **Dono de**: `api/auth/**`, `api/workspace/**` (controllers/dtos), `application/auth/`, `application/workspace/`, `domain/model/{User,Workspace,Member}`, `infrastructure/security/**`. Após E1, é dono das pastas compartilhadas de segurança/erro.
- **Passos**: entidades JPA User/Workspace/Member → SecurityConfig (sessão, CSRF para SPA, CORS dev) → endpoints do contrato → `WorkspaceAccessFilter` (resolve membership, expõe `WorkspaceContext` request-scoped) → seeding de workspace no registro (categorias de `category_template` + `pillar_allocation` 30/10/20/10/25/5 — coordenar formato com BE-2, que é o dono da lógica de seed).
- **Aceite**: registro cria user+workspace+member OWNER+seeds; login/logout funcionais via cookie; acesso a workspace alheio → 403 `NOT_A_MEMBER`; testes de integração cobrindo os 3 fluxos + isolamento.

### BE-2 — Contas, Categorias & Pilares `[E1 · spring-boot-engineer]`

- **Objetivo**: CRUDs de Account e Category, enum Pillar, `PillarAllocation` fixa, seed de categorias padrão.
- **Entradas**: PLANEJAMENTO §6.2, §6.4, §4; contrato: tags `accounts`, `categories`, `pillars`.
- **Dono de**: `api/account/**`, `api/category/**`, `application/account/`, `application/category/`, `domain/model/{Account,AccountType,Category,CategoryType,Pillar,PillarAllocation}` e a lógica de seed (`WorkspaceDefaultsSeeder`) chamada por BE-1.
- **Regras críticas**: categoria EXPENSE exige pillar, INCOME proíbe (constraint já em V1); conta com transação não é excluível — só arquivável (`ACCOUNT_HAS_TRANSACTIONS`); conta INVESTMENT/SAVINGS aceita `default_contribution_pillar` (GOALS | FINANCIAL_FREEDOM).
- **Aceite**: CRUDs completos com validação; saldo NÃO entra aqui (é de BE-3 — endpoint de contas retorna saldo `null` até E2, documentado no contrato); testes por regra crítica.

### FE-1 — Fundação visual `[E1 · react-specialist]`

- **Objetivo**: app shell navegável com tema claro/escuro, i18n pt-BR, autenticação, e a esteira de dados mockada (orval + TanStack Query + MSW) que destrava FE-2/3/4.
- **Entradas**: PLANEJAMENTO §3.2, §3.3, §6.1; contrato completo (gera cliente de tudo).
- **Dono de**: `web/src/**` exceto `features/` de FE-2/3/4. Estabelece: `components/ui/` (shadcn: button, input, card, dialog, select, table, tabs, sonner…), `components/layout/AppShell`, `lib/api/` (orval output + fetch client com credenciais), `i18n/`, rotas (`/login`, `/register`, `/` shell com sidebar: Dashboard, Lançamentos, Contas, Cartões, Categorias), guarda de autenticação, `lib/format.ts` (BRL via `Intl`, datas pt-BR).
- **Aceite**: `npm run api:generate` regenera cliente+mocks sem edição manual; fluxo login/registro completo contra MSW; navegação entre páginas placeholder; tema persiste; zero string hardcoded; testes de render das rotas.

### BE-3 — Transações, Transferências & Aportes `[E2 · spring-boot-engineer]`

- **Objetivo**: lançamentos INCOME/EXPENSE com status SETTLED/PENDING, transferências como par espelhado, **aporte** (transferência para conta investimento com `contribution_pillar`), motor de saldo atual/projetado, listagem com filtros.
- **Entradas**: PLANEJAMENTO §6.3, §5 regras 4, 5, 9; contrato: tags `transactions`, `transfers`.
- **Dono de**: `api/transaction/**`, `application/transaction/`, `domain/model/{Transaction,TransactionType,TransactionStatus}`, `domain/service/BalanceCalculator`.
- **Regras críticas**: transferência cria/edita/exclui os DOIS lados atomicamente (`transfer_pair_id`); transferência nunca aparece como receita/despesa em agregações; aporte exige destino INVESTMENT/SAVINGS e pillar ∈ {GOALS, FINANCIAL_FREEDOM} (default da conta, sobrescrevível); saldo = initial + Σ SETTLED, projetado inclui PENDING (regra 5); efetivação de pendente em um clique (endpoint `settle`).
- **Aceite**: testes de unidade do BalanceCalculator; integração dos fluxos de par de transferência (criar/editar/excluir); filtros (período, conta, categoria, tipo, texto) paginados.

### BE-4 — Cartão de crédito `[E2 · java-architect]` ⚠️ frente mais complexa

- **Objetivo**: Card CRUD, compras à vista/parceladas, motor de alocação de faturas (Statement), pagamento total/parcial debitando conta.
- **Entradas**: PLANEJAMENTO §6.5, §5 regras 1, 2, 3, 6; contrato: tags `cards`, `statements`.
- **Dono de**: `api/card/**`, `application/card/`, `domain/model/{Card,CardPurchase,Installment,Statement,StatementStatus,StatementPayment}`, `domain/service/{StatementAllocator,InstallmentSplitter}`.
- **Regras críticas**: `InstallmentSplitter` — divisão em centavos com resto na última (100/3 → 3333+3333+3334); `StatementAllocator` — compra ≤ closing_day → fatura corrente, senão próxima; parcelas em faturas consecutivas; statements criados on-demand (lazy) por `(card, reference_month)`; editar/excluir compra parcelada afeta só parcelas de faturas não pagas — fatura PAID é imutável (`STATEMENT_IMMUTABLE`); pagamento gera despesa na conta (transaction vinculada via `statement_payment.transaction_id`, categoria sistêmica "Pagamento de fatura" excluída dos relatórios por categoria — regra 2); pagamento parcial → status PARTIAL, sem juros (decisão §9.5); limite disponível = limit − Σ faturas não pagas.
- **Aceite**: bateria de unidade exaustiva de `StatementAllocator` + `InstallmentSplitter` (fechamento no dia 28+meses curtos, compra no dia do fechamento, 12 parcelas cruzando ano); integração compra→fatura→pagamento; **não toca** em `application/transaction/` — cria a despesa de pagamento via API interna de BE-3 (`TransactionService`), nunca por INSERT próprio.

### FE-2 — Telas de contas, categorias e extrato `[E2 · react-specialist]`

- **Objetivo**: telas de Contas (lista+saldos, form, arquivar), Categorias (árvore por pilar, form), Lançamento rápido (modal ≤5 interações — receita/despesa/transferência/aporte) e Extrato (filtros, edição, efetivar pendente).
- **Entradas**: PLANEJAMENTO §6.2–6.4; contrato: mocks MSW de accounts/categories/transactions.
- **Dono de**: `web/src/features/accounts/**`, `features/categories/**`, `features/transactions/**`.
- **Aceite**: fluxos completos contra MSW; lançamento rápido abre de qualquer tela (atalho no shell — pedir slot a FE-1, não editar o shell); aporte mostra seletor de pilar pré-preenchido quando destino é INVESTMENT; testes dos forms (validação, submissão).

### BE-5 — Dashboard, Alocação por pilar & Export `[E3 · spring-boot-engineer]`

- **Objetivo**: endpoints agregados do dashboard e export CSV.
- **Entradas**: PLANEJAMENTO §6.6, §5 regras 2, 9, 10; contrato: tags `dashboard`, `export`.
- **Dono de**: `api/dashboard/**`, `application/dashboard/`, `domain/service/PillarAllocationCalculator`, `api/export/**`.
- **Regras críticas**: alocação do mês por pilar = (despesas por competência — compras de cartão contam na data da compra, não do pagamento — + aportes do pilar) ÷ receitas do mês (regra 10); mês sem receita → percentuais nulos + valores absolutos com flag `noIncome`; transferências fora de tudo; pagamento de fatura fora dos gastos por categoria (regra 2); comparativo mês anterior; CSV de transações com filtros (UTF-8 BOM, separador `;` — Excel BR).
- **Aceite**: testes de unidade do `PillarAllocationCalculator` cobrindo regras 2/9/10 (cenário canônico: salário + compras cartão + aporte + transferência → percentuais conferidos à mão); performance: agregações via query, não em memória.

### FE-3 — Telas de cartão e fatura `[E3 · react-specialist]`

- **Objetivo**: Cartões (lista com limite disponível, form), Fatura (navegação entre meses, itens com parcela x/y, status, pagar total/parcial), compra no cartão no lançamento rápido (parcelamento com preview das parcelas).
- **Entradas**: PLANEJAMENTO §6.5; contrato: mocks de cards/statements.
- **Dono de**: `web/src/features/cards/**`.
- **Aceite**: fluxo compra parcelada → fatura → pagamento contra MSW; preview de parcelas confere com a regra do resto na última; badge de vencimento próximo.

### FE-4 — Dashboard `[E3 · react-specialist]`

- **Objetivo**: tela inicial — alocação por pilar (barras % vs alvo 30/10/20/10/25/5, destaque de estouro), saldo total/por conta (com/sem investimentos), receitas × despesas, gastos por categoria com drill-down por pilar, faturas abertas e próximos vencimentos, comparativo mês anterior.
- **Entradas**: PLANEJAMENTO §6.6; contrato: mocks de dashboard. Gráficos: Recharts (adicionar dependência — já prevista no contrato de FE-1? se não, adicionar aqui).
- **Dono de**: `web/src/features/dashboard/**`.
- **Aceite**: estado `noIncome` tratado (valores absolutos + aviso); responsivo mobile (é a tela mais usada no celular); testes de render com dados mock dos 3 estados (normal, estouro, sem receita).

### INT-1 — Integração & entrega `[E4 · java-architect, sequencial]`

- **Objetivo**: ligar tudo de verdade e entregar o MVP rodando em Docker.
- **Passos**: trocar MSW pela API real (flag `VITE_API_MOCK=false`; MSW permanece para testes); revisar divergências contrato×implementação (springdoc `/v3/api-docs` vs `contracts/openapi.yaml` — o contrato é atualizado para refletir a realidade e o cliente regenerado); smoke E2E (Playwright: registro → conta → lançamento → compra parcelada → pagamento fatura → dashboard confere); PWA (manifest, service worker via `vite-plugin-pwa`, instalável); build docker final (`docker compose up` completo com Caddy TLS interno, backup testado com restore real de um dump); revisão de acessibilidade básica e i18n (nenhuma chave faltando).
- **Aceite**: `docker compose up -d` numa máquina limpa serve o app funcional em HTTPS; E2E verde; dump de backup restaurável.

---

## 5. Gates de verificação entre etapas

Rodar ao final de cada etapa, antes de liberar a próxima:

```bash
cd mk-finance/api && ./mvnw verify          # exige Docker (Testcontainers)
cd mk-finance/web && npm test && npm run build
docker compose -f mk-finance/docker/docker-compose.yml config -q
```

Gate adicional por etapa — E1: registro/login manual via `curl` + front logando contra MSW; E2: cenário canônico de cartão validado (compra dia do fechamento, 3 parcelas, pagamento); E3: percentuais de alocação conferidos manualmente contra planilha do cenário canônico; E4: aceite do INT-1.

## 6. Estado atual (E0 + E1 + E2 concluídas)

> **E2 concluída em 22/07/2026 — gate verde**: backend 151 testes, front 44 testes, build e `docker compose config` OK, cenário canônico do cartão validado.
> **O handoff operacional vive em `CONTEXTO-E3.md`** — leia-o antes de iniciar a E3. Ele traz o que a E2 entregou, as pendências abertas (patches de contrato, saldos de conta) e as armadilhas de orquestração descobertas.
>
> Resolvido nesta etapa: **Lombok nas entidades JPA, `record` nos DTOs** (decisão do usuário, 21/07/2026) — encerra a nota "a confirmar" abaixo.
> Migrações aplicadas: V1, V2, **V7** (BE-3), **V9** (BE-4). V3–V6, V8, V10, V11 ficaram vagas — não renumere. BE-5 usa V12.

### 6.1 E0 + E1

**Fundação (E0)**: `mk-finance/` com contrato `openapi.yaml`, `api/` Spring Boot 3.5/Java 21/Maven, `web/` Vite/React/TS + orval/MSW/i18n, `docker/` (compose+Caddy+Dockerfiles), personas em `.claude/agents/`.

**E1 — Base (verde no gate):**
- Backend (`api/`): auth (Spring Security, BCrypt, cookie `MK_FINANCE_SESSION`, CSRF SPA via `CookieCsrfTokenRepository`), workspaces + convites, **isolamento por workspace** via `WorkspaceContext` request-scoped + `WorkspaceAccessInterceptor` (403 `NOT_A_MEMBER`) — reusar nas próximas frentes, não reimplementar. `WorkspaceDefaultsSeeder` copia `category_template` e semeia alocação 30/10/20/10/25/5. CRUDs de contas/categorias/pilares com as regras (pilar obrigatório em EXPENSE, archive-vs-delete). Guards de exclusão já preveem transações/compras (BE-3/BE-4). 18 testes de integração Testcontainers, `mvnw verify` verde. Ajustes de infra: `context-path: /api`, CORS dev, **Lombok** adicionado ao `pom.xml` (decisão a confirmar — ver nota).
- Frontend (`web/`): app shell responsivo + navegação, tema claro/escuro persistido (`useTheme` zustand), i18n pt-BR, auth (login/registro + `RequireAuth`), MSW com sessão stateful em `src/mocks/handlers.ts` (auth via localStorage p/ exercitar o guard), infra de teste compartilhada (`renderWithProviders`, `authFixtures`, MSW node). `build` + 9 testes verdes.

**Convenções emergentes p/ FE-2/3/4** (do relatório FE-1): chaves i18n por namespace de feature; erros via `translateApiError(t, error)` em `onError`; hooks gerados de `@/lib/api/generated/<tag>/<tag>`; nunca editar `generated/**`; `setQueryData(key, undefined)` é no-op (usar `removeQueries`); `features/<área>/**` é do dono da ficha, resto de `src/` é infra FE-1.

**Notas/decisões a confirmar**:
- **Lombok** no backend diverge da nota do E0 ("sem Lombok, records"). Aceitável para entidades JPA (que não podem ser records); DTOs poderiam ser records. Decidir se mantém.
- `fetcher.ts` tem um contorno de ambiente de teste (jsdom + AbortSignal), inócuo em produção.
- Integração real (context-path `/api`, fluxo CSRF `XSRF-TOKEN`→`X-XSRF-TOKEN`) validada só na INT-1.

**Pendências para o usuário**: ativar git antes de E2 (recomendado — habilita worktrees para o paralelismo de 3 frentes), domínio real para DNS-01 (só afeta E4/deploy).
