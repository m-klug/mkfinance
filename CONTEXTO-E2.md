# Contexto para execução da E2 — MK Finance

> **Leia este arquivo primeiro.** Ele é o handoff completo para iniciar a Etapa E2 num chat novo.
> Detalhamento: `PLANEJAMENTO.md` (fonte da verdade de negócio) e `DESENVOLVIMENTO-MVP.md` (fichas e convenções).
> Atualizado em 20/07/2026, após a E1 concluída.

## 1. O projeto

App de controle financeiro pessoal, **self-hosted** (Proxmox + LXC, acesso via WireGuard e DNS interno). Uso pessoal, mas modelado como **multiusuário por espaço (`Workspace`)** desde o início. Organiza despesas pelo **método dos 6 pilares** com alvos fixos no MVP: Custos Fixos 30, Conforto 10, Metas 20, Prazeres 10, Liberdade Financeira 25, Conhecimento 5.

## 2. Repositório e stack

Monorepo (decisão consciente: o contrato OpenAPI é o ponto de sincronização entre back e front). Branch `main`, remote `git@github.com:m-klug/mkfinance.git`.

| Caminho | Stack |
|---|---|
| `mk-finance/api/` | Java 21 + Spring Boot 3.5 + Maven (`./mvnw`), Flyway, pacote `br.com.mkfinance` |
| `mk-finance/web/` | Vite 6 + React 19 + TS estrito, Tailwind 4, shadcn/ui, TanStack Query, i18n pt-BR, orval + MSW |
| `mk-finance/contracts/openapi.yaml` | **O contrato** — fonte única entre back e front |
| `mk-finance/docker/` | compose (caddy, api, web, postgres, backup) |
| `.claude/agents/` | personas: `java-architect`, `spring-boot-engineer`, `react-specialist` |

Ambiente: Java 21, Node 24, Docker disponível (Testcontainers sobe Postgres real). Não há Postgres local — testes usam Testcontainers; o dev do front roda contra MSW.

## 3. Estado atual: E0 e E1 concluídas (gate verde)

**Backend** (18 testes de integração Testcontainers, `./mvnw verify` verde):
- Auth: registro/login/logout/me com Spring Security, BCrypt, sessão em cookie `MK_FINANCE_SESSION`, CSRF padrão SPA (`CookieCsrfTokenRepository.withHttpOnlyFalse()`), CORS para `http://localhost:5173`.
- Workspaces + convites por link.
- CRUDs de contas, categorias e pilares, com as regras (pilar obrigatório em EXPENSE / proibido em INCOME; arquivar-em-vez-de-excluir).
- Schema completo do MVP já existe em `V1__schema.sql` (inclui `transaction`, `card`, `card_purchase`, `installment`, `statement`, `statement_payment` — **a E2 não precisa criar tabelas**), e `V2__seed.sql` tem `category_template`.

**Frontend** (build limpo, 9 testes):
- App shell responsivo (sidebar + drawer mobile), tema claro/escuro persistido, i18n pt-BR, login/registro com `RequireAuth`, MSW ligado aos mocks gerados do contrato (`VITE_API_MOCK=true` em `.env.development`).
- Telas internas ainda são placeholders — é o que a E2/E3 preenche.

## 4. Convenções invioláveis (herdadas por toda ficha)

1. **Código 100% em inglês** com o glossário PT→EN de `PLANEJAMENTO.md` §3.3 (Fatura=`Statement`, Aporte=`Contribution`, Espaço=`Workspace`, Parcela=`Installment`…).
2. **Dinheiro é `long` em centavos**, sempre. Nunca float/double/BigDecimal no domínio.
3. **Datas de negócio** em `LocalDate` (timezone de referência `America/Sao_Paulo`); `TIMESTAMPTZ` só para auditoria.
4. **Isolamento por workspace**: toda query filtra por `workspace_id`.
5. **Erros**: código de máquina (`STATEMENT_ALREADY_PAID`…) no shape `ErrorResponse{code, params?, fieldErrors?}`. Backend nunca devolve texto em português — o front traduz.
6. **i18n**: zero string hardcoded em JSX; tudo via `t('chave')` em `src/i18n/locales/pt-BR/common.json`.
7. **Migrações Flyway são append-only** — nunca editar V1/V2. Reserva: BE-3 → V7–V8, BE-4 → V9–V11.
8. **Só o `java-architect` edita o contrato.** Se uma frente precisar mudar o contrato, ela PARA e reporta.
9. **Nunca editar `web/src/lib/api/generated/**`** — é regenerado por `npm run api:generate`.

## 5. O que a E1 deixou pronto — REUSE, não reimplemente

**Backend:**
- `infrastructure/security/WorkspaceContext` (request-scoped) — injete por construtor e use `getWorkspaceId()` / `getUserId()` / `getRole()` / `isOwner()`. O `WorkspaceAccessInterceptor` já resolve a associação e devolve 403 `NOT_A_MEMBER`. **Não reparseie o path nem reconsulte membership.**
- `domain/exception/AppException` + `ErrorCode` + `api/error/GlobalExceptionHandler` — adicione novos códigos ali.
- `application/workspace/WorkspaceDefaultsSeeder` — semeia categorias e alocação no registro.
- Guards já existentes: `AccountRepository.existsTransactionForAccount`, `CategoryRepository.existsTransactionForCategory` / `existsCardPurchaseForCategory`.
- Layout: `domain/` (model, service puro, exception), `application/` (serviços transacionais), `api/` (controller, dto, mapper), `infrastructure/`.

**Frontend:**
- Hooks gerados em `@/lib/api/generated/<tag>/<tag>` (`useListAccounts`, `useCreateAccount`…).
- `translateApiError(t, error)` de `@/lib/errors` no `onError` de toda mutation → `toast.error(...)`.
- Testes: `renderWithProviders` (`@/test/renderWithProviders`), `seedAuthenticatedSession()` (`@/test/authFixtures`), MSW node já ativo no `src/test/setup.ts`; use `server.use(...)` para cenários pontuais.
- Formatadores em `@/lib/format` (`formatCents`, `formatDate`, `formatMonth`).
- ⚠️ `queryClient.setQueryData(key, undefined)` é **no-op** no TanStack v5 — use `removeQueries`.
- Ownership: `src/features/<área>/**` é da ficha; o resto de `src/` é infra compartilhada da FE-1.

## 6. A Etapa E2 — três frentes paralelas

Com git ativo, rode as três **isoladas em git worktrees**. Fichas completas em `DESENVOLVIMENTO-MVP.md` §4.

### BE-3 — Transações, Transferências & Aportes `[spring-boot-engineer]`
Dono de `api/**/transaction/**`, `domain/service/BalanceCalculator`. Endpoints das tags `transactions` e `transfers`.
- Lançamentos INCOME/EXPENSE com status SETTLED/PENDING + endpoint `settle` (efetivar em 1 clique).
- **Transferência = par espelhado** (`transfer_pair_id`), criado/editado/excluído atomicamente nos dois lados. Transferência **nunca** é receita nem despesa em agregações (R4).
- **Aporte** (R9): transferência cujo destino é conta INVESTMENT/SAVINGS. Não é despesa, mas carrega `contribution_pillar` (GOALS | FINANCIAL_FREEDOM, default vindo da conta) para consumir o pilar no relatório de alocação.
- Saldo (R5): `initial + Σ SETTLED`; projetado inclui PENDING.
- Aceite: testes de unidade do `BalanceCalculator` + integração do par de transferência; filtros paginados.

### BE-4 — Cartão de crédito `[java-architect]` ⚠️ frente mais delicada
Dono de `api/**/card/**`, `domain/service/{StatementAllocator,InstallmentSplitter}`. Tags `cards` e `statements`.
- **`InstallmentSplitter` (R1)**: divisão em centavos com **resto na última parcela** (100,00/3 → 33,33 + 33,33 + 33,34).
- **`StatementAllocator` (R3)**: compra até o `closing_day` entra na fatura corrente; após, na seguinte. Parcelas caem em faturas **consecutivas**. Faturas são criadas sob demanda por `(card, reference_month)`.
- **R6**: editar/excluir compra parcelada afeta só parcelas em faturas não pagas; fatura PAID é imutável (`STATEMENT_IMMUTABLE`).
- **R2**: o pagamento da fatura gera despesa na conta com categoria sistêmica (`system=true`), **excluída dos relatórios por categoria**; o gasto conta por competência (data da compra).
- Pagamento parcial → status PARTIAL, **sem juros rotativos** (decisão do usuário, §9.5 do PLANEJAMENTO).
- **Não toque em `application/transaction/`** — crie a despesa de pagamento chamando o `TransactionService` de BE-3, nunca por INSERT próprio.
- Aceite: bateria exaustiva de unidade (fechamento dia 28 + meses curtos, compra no dia do fechamento, 12 parcelas cruzando o ano) + integração compra→fatura→pagamento.

### FE-2 — Telas de contas, categorias e extrato `[react-specialist]`
Dono de `web/src/features/{accounts,categories,transactions}/**`. Trabalha contra MSW.
- Contas (lista com saldos, form, arquivar), Categorias (árvore por pilar), **Lançamento rápido** (modal ≤5 interações: receita/despesa/transferência/aporte, com seletor de pilar quando destino é investimento) e Extrato (filtros, edição, efetivar pendente).

## 7. Gate da E2 (rodar antes de liberar a E3)

```bash
cd mk-finance/api && ./mvnw verify        # exige Docker
cd mk-finance/web && npm test && npm run build
POSTGRES_PASSWORD=x docker compose -f mk-finance/docker/docker-compose.yml config -q
```
Gate específico: validar o **cenário canônico do cartão** — compra no dia do fechamento, parcelada em 3, e o pagamento da fatura debitando a conta.

## 8. Decisões e pendências em aberto

- **Lombok** foi adicionado ao backend na E1, divergindo da nota original ("sem Lombok, usar records"). Defensável para entidades JPA (que não podem ser records); DTOs poderiam ser records. **Aguarda decisão do usuário.**
- **Integração real** (front ↔ API) só acontece na INT-1/E4: hoje o front usa MSW por design. Pontos a acertar lá: `context-path: /api` no backend e o fluxo CSRF (GET inicial → cookie `XSRF-TOKEN` → ecoar em `X-XSRF-TOKEN`).
- **Visibilidade do repo no GitHub** não foi verificada (`gh` CLI não instalado nesta máquina).
- Rodar o app hoje: `cd mk-finance/web && npm run dev` (http://localhost:5173, contra mocks, sem backend/banco).
