# Contexto para execução da E3 — MK Finance

> **Leia este arquivo primeiro.** Handoff completo para iniciar a Etapa E3 num chat novo.
> Detalhamento: `PLANEJAMENTO.md` (fonte da verdade de negócio) e `DESENVOLVIMENTO-MVP.md` (fichas e convenções).
> Atualizado em 22/07/2026, após a E2 concluída. Substituiu o `CONTEXTO-E2.md`, removido do repositório (recuperável no histórico do git, commit `ed7297d`).

## 1. O projeto

App de controle financeiro pessoal, **self-hosted** (Proxmox + LXC, acesso via WireGuard e DNS interno). Uso pessoal, mas modelado como **multiusuário por espaço (`Workspace`)** desde o início. Organiza despesas pelo **método dos 6 pilares** com alvos fixos no MVP: Custos Fixos 30, Conforto 10, Metas 20, Prazeres 10, Liberdade Financeira 25, Conhecimento 5.

## 1.1 Repositório e stack

Monorepo (decisão consciente: o contrato OpenAPI é o ponto de sincronização entre back e front). Branch `main`, remote `git@github.com:m-klug/mkfinance.git`.

| Caminho | Stack |
|---|---|
| `mk-finance/api/` | Java 21 + Spring Boot 3.5 + Maven (`./mvnw`), Flyway, pacote `br.com.mkfinance` |
| `mk-finance/web/` | Vite 6 + React 19 + TS estrito, Tailwind 4, shadcn/ui, TanStack Query, i18n pt-BR, orval + MSW |
| `mk-finance/contracts/openapi.yaml` | **O contrato** — fonte única entre back e front |
| `mk-finance/docker/` | compose (caddy, api, web, postgres, backup) |
| `.claude/agents/` | personas: `java-architect`, `spring-boot-engineer`, `react-specialist` |

Ambiente verificado nesta máquina: Java 21.0.5, Node 24.14.1, Docker rodando. Não há Postgres local — os testes de backend usam Testcontainers (Postgres real) e o dev do front roda contra MSW.

Rodar o app hoje: `cd mk-finance/web && npm run dev` (http://localhost:5173, contra mocks, sem backend/banco).

## 2. Estado atual: E0, E1 e E2 concluídas (gate verde)

Gate da E2 rodado sobre a `main` já integrada:

| Eixo | Resultado |
|---|---|
| `./mvnw verify` | **175 testes, 0 falhas, BUILD SUCCESS** (151 na E2 + 24 nas passadas pré-E3) |
| `npm test` | **44 testes, 8 arquivos** |
| `npm run build` | verde |
| `docker compose config -q` | OK |
| Cenário canônico do cartão | ✅ validado (ver §3) |

**Backend** — além da E1 (auth, workspaces, contas, categorias, pilares):
- **BE-3**: lançamentos INCOME/EXPENSE com SETTLED/PENDING + `settle`; transferência como par espelhado atômico; aporte com `contribution_pillar`; `BalanceCalculator` puro; filtros paginados.
- **BE-4**: cartões, compras à vista/parceladas, `InstallmentSplitter`, `StatementAllocator`, faturas lazy por `(card, reference_month)`, pagamento total/parcial debitando conta via `TransactionService`.

**Frontend** — além da E1 (shell, tema, i18n, auth):
- **FE-2**: telas de Contas, Categorias, Lançamento rápido (4 interações) e Extrato, contra MSW.

Migrações aplicadas: `V1`, `V2` (E0/E1), **`V7`** (BE-3 — `transfer_direction` + invariante da perna de aporte), **`V9`** (BE-4 — índice único parcial da categoria sistêmica). V3–V6, V8, V10, V11 ficaram **vagas — não renumere**. Reserva da BE-5: **V12**.

## 3. O cenário canônico do cartão (gate da E2, já verde)

`StatementPaymentIntegrationTest.CanonicalScenario` — compra de R$ 100,00 **no dia 20, que é o próprio fechamento**, em 3×. Verifica: parcelas `3333/3333/3334` (R1), faturas `2026-07/08/09` com a compra **na fatura corrente** e não na seguinte (R3), fechamento 20/07 e vencimento 05/08, e o pagamento gerando `EXPENSE SETTLED` de 3333 na conta sob categoria `system=true` (R2), com as parcelas restantes intactas.

## 4. Convenções invioláveis (herdadas por toda ficha)

1. **Código 100% em inglês** com o glossário PT→EN de `PLANEJAMENTO.md` §3.3.
2. **Dinheiro é `long` em centavos**, sempre.
3. **Datas de negócio** em `LocalDate` (`America/Sao_Paulo`); `TIMESTAMPTZ` só para auditoria.
4. **Isolamento por workspace**: toda query filtra por `workspace_id`.
5. **Erros**: código de máquina no shape `ErrorResponse{code, params?, fieldErrors?}`. Backend nunca devolve texto em português.
6. **i18n**: zero string hardcoded em JSX.
7. **Migrações Flyway são append-only.**
8. **Só o `java-architect` edita o contrato.**
9. **Nunca editar `web/src/lib/api/generated/**`.**
10. **Lombok nas entidades JPA, `record` nos DTOs** — decisão do usuário em 21/07/2026, resolve a pendência aberta na E2.

## 5. O que a E2 deixou pronto — REUSE, não reimplemente

**Backend:**
- `application/transaction/TransactionService.createSettledExpense(workspaceId, accountId, categoryId, amountCents, date, description)` — a forma canônica de gerar uma despesa liquidada a partir de outra feature. `delete(workspaceId, transactionId)` desfaz.
- `application/transaction/AccountBalanceService.balancesByAccount(workspaceId)` — saldos de um workspace inteiro em uma query agregada. **Existe e está testado, mas ainda não está ligado à API** (ver §6.4).
- `domain/service/BalanceCalculator` — puro, sem Spring.
- `domain/service/{InstallmentSplitter,StatementAllocator}` — puros.
- `application/card/BusinessClock` — "hoje" em `America/Sao_Paulo`. Está no pacote de card por ter nascido lá; **a BE-5 pode promovê-lo** para um pacote compartilhado se precisar.
- `Statement.effectiveStatus(today)` — `CLOSED` é **derivado, não persistido**. A coluna guarda só OPEN/PARTIAL/PAID.
- Total de fatura **nunca é armazenado** — é agregado no banco por `StatementTotalsLoader`.

**Frontend:**
- `components/form/fields.tsx` e `money.ts`, `components/ConfirmDialog.tsx`, `hooks/useCurrentWorkspace.ts`, `test/financeFixtures.ts` — infra criada pela FE-2, disponível para FE-3/FE-4.
- `QuickTransactionButton` está montado no `AppShell` (header); o estado vive todo no componente da FE-2.
- Formatadores em `@/lib/format`; `translateApiError` em `@/lib/errors`.

## 6. Pendências abertas — decisões do usuário

### 6.1–6.4 — ✅ RESOLVIDOS na passada de contrato pré-E3 (22/07/2026)

Executada com nada mais rodando em paralelo, para não quebrar mocks de nenhuma frente. Gate: **165 testes backend** (151 + 14), **44 front**, build verde, regeneração do cliente sem nenhuma quebra (todas as mudanças foram aditivas).

- **6.1 `transferDirection`** — exposto no schema `Transaction` (+ enum `TransferDirection`). O extrato já pode distinguir entrada de saída.
- **6.2 Arquivar cartão** — `POST /cards/{cardId}/archive`, espelhando contas. Semântica confirmada por teste: fatura de cartão arquivado continua **legível e pagável**; só compras novas são recusadas.
- **6.3 Códigos de erro** — sincronizados e reagrupados por área. Além dos 5 da BE-4, a BE-3 tinha outros 3 nunca listados: `CATEGORY_TYPE_MISMATCH`, `TRANSACTION_TYPE_IMMUTABLE`, `TRANSACTION_NOT_FOUND`.
- **6.4 Saldos de conta** — `balanceCents`/`projectedBalanceCents` agora preenchidos. Ligados no `AccountController` (não no `AccountService`), espelhando o precedente de `CardController`/`availableLimitByCard` e evitando dependência `application/account → application/transaction`. Uma query agregada por listagem, sem N+1; `create()` pula a query.

**Nota de qualidade**: os dois testes da BE-2 que fixavam `balanceCents` em `null` foram atualizados para valores reais — eles codificavam justamente o comportamento incompleto que esta passada completou. Não foi teste dobrado para esconder quebra; a suíte ganhou um teste novo que confere a aritmética (100.000 + 20.000 − 30.000 = 90.000 atual, 85.000 projetado com um pendente).

**Débito técnico introduzido conscientemente**: `TransferService.update` não aceita `contributionPillar` (sua assinatura recebe `TransactionInput`, que não tem o campo). Para não alterar código da BE-3, a passada criou `api/transaction/TransferUpdateHandler` (`@Transactional`), que grava o pilar pedido na perna de entrada antes de delegar — funciona pela garantia de identidade do JPA dentro da mesma transação, e o motor valida igual à criação. **É orquestração na camada de API por respeito à fronteira de propriedade, não o lugar certo.** Ação para a E3: mover para uma sobrecarga de `TransferService.update` e aposentar o handler.

**Consistência corrigida de brinde**: `POST /transfers` *rejeitava* um pilar apontado a destino comum (`INVALID_CONTRIBUTION_PILLAR`), enquanto o update o *descartava em silêncio*. O update passou a concordar com a criação — checando o veredito do próprio motor, não re-derivando a regra, para ela seguir existindo num lugar só. Omitir o pilar continua significando "não mexer".

### 6.5 Estorno de pagamento de fatura — ✅ RESOLVIDO (22/07/2026)

**Decisão do usuário: opção A + C.** Gate: **175 testes backend** (165 + 10), 44 front, build verde. Sem migração — estorno é DELETE de linha.

- **A** — `DELETE /workspaces/{workspaceId}/statements/{statementId}/payments/{paymentId}`. Remove a linha e a despesa vinculada (via `TransactionService.delete` de BE-3) na mesma transação; o status volta sozinho a OPEN/PARTIAL porque é **derivado** de `StatementTotals`, e o dinheiro retorna ao saldo.
- **C** — excluir a despesa de pagamento pelo `/transactions` agora devolve `TRANSACTION_LOCKED_BY_PAYMENT` em vez de `DATA_INTEGRITY_VIOLATION` genérico, apontando para a tela da fatura. Implementado como guard de repositório, seguindo o precedente da E1 (`existsCardPurchaseForCategory`).

**Como "estornar" foi separado de "lançar cobrança"** (era o risco central da tarefa): `Statement.isImmutable()` era um booleano único consultado por dois chamadores com intenções diferentes. Foi renomeado para **`chargesFrozen()`**, que agora tem **um único chamador** (`CardPurchaseService.requireAllMutable`) — a R6 segue intacta. O `pay()` deixou de consultar status persistido e recusa por `remaining == 0`, derivado como todo o resto da feature. O `reversePayment()` não consulta nenhum dos dois. O teste `reversibilityDoesNotWeakenTheChargeFreeze` fixa que fatura PAID **recusa compra nova e aceita estorno** na mesma asserção.

**Detalhe de implementação que importa**: o DELETE da linha é *flushed* explicitamente antes de chamar `TransactionService.delete`, porque o guard novo lê `statement_payment` por SQL nativo e não pode enxergar a linha em remoção. Depender do auto-flush do Hibernate ali seria frágil.

**Adição além do briefing, sinalizada pela frente**: `StatementDetail` passou a devolver o array `payments`. Sem isso **não havia `paymentId` alcançável** — `paidCents` diz que foi pago, mas não qual pagamento desfazer. Aditivo ao contrato.

⚠️ **Pendente para a FE-3**: a UI de estorno. `useReverseStatementPayment` e o array `payments` já estão gerados. Falta a tradução de `STATEMENT_PAYMENT_NOT_FOUND` (só a tela de fatura alcança). E o texto de `STATEMENT_IMMUTABLE` ("Faturas pagas não podem ser alteradas") ficou largo demais agora que pagamentos **são** reversíveis — só é emitido para cobranças, então não está errado, mas a tela merece redação mais precisa.

### 6.6 Transferência não editável — ✅ RESOLVIDO (defeito de contrato, não decisão de produto)
Diagnóstico correto: o backend **sempre soube** editar transferência. `TransferService.update` altera as duas pernas atomicamente (valor, data, descrição, nota, status, conta da perna, e re-derivação do pilar do aporte), com 14 testes de integração, e o `TransactionController` já roteava o `PATCH` para lá.

O que travava era uma **contradição interna do contrato**: a descrição do `PATCH /transactions/{id}` prometia *"Editing one leg of a transfer updates its pair atomically"*, mas o schema `TransactionRequest` declarava `type: enum [INCOME, EXPENSE]` e exigia `categoryId` — que uma transferência não tem. O cliente TypeScript gerado não conseguia montar payload válido, então a FE-2 escondeu o botão.

**Decisão do usuário (22/07/2026): opção A** — endpoint dedicado `PATCH /transfers/{transactionId}` com payload próprio, mantendo o `TransactionRequest` rigoroso (exigir `categoryId` em INCOME/EXPENSE é validação que vale preservar). Aplicado na passada de contrato pré-E3.

⚠️ **Pendente para a E3**: reexibir o botão de editar em linhas TRANSFER no extrato (`features/transactions/`). O contrato e o backend estão destravados; falta só a UI.

### 6.7 Selects nativos — ✅ DECIDIDO: manter
**Decisão do usuário (22/07/2026): manter os `<select>` nativos estilizados.** Razões que sustentam a escolha: picker do SO no mobile (o app é majoritariamente mobile), acessibilidade de teclado e leitor de tela sem trabalho extra, e evita os stubs de `hasPointerCapture`/`scrollIntoView` que o Radix exige no jsdom.

**FE-3 e FE-4 devem seguir o mesmo padrão** — não introduzir o `Select` do shadcn em forms novos, sob pena de inconsistência visual entre telas. O componente do shadcn continua no repositório, intocado.

### 6.8 Bundle cruzou 500 kB
O build agora emite aviso de chunk >500 kB (535,98 kB / 164,86 kB gzip). **É novo da E2** — no baseline da E1 eram 483,66 kB sem aviso. Não é erro, é dica de code-splitting. A FE-4 vai adicionar Recharts e piorar isso: considere `manualChunks` ou import dinâmico.

### 6.9 Pendências herdadas da E2, ainda válidas
- **Integração real** front↔API só na INT-1. Pontos a acertar: `context-path: /api` no backend e o fluxo CSRF (GET inicial → cookie `XSRF-TOKEN` → ecoar em `X-XSRF-TOKEN`).
- **Visibilidade do repo no GitHub**: ✅ **privado** (confirmado pelo usuário em 22/07/2026). O `gh` CLI não está instalado nesta máquina, então a confirmação é do usuário, não verificada por ferramenta.
- **Push pendente**: o trabalho da E2 e das passadas pré-E3 está commitado só localmente. `main` está à frente de `origin/main`; enviar é decisão do usuário.

## 7. Armadilhas operacionais descobertas na E2 (leia antes de orquestrar a E3)

1. **Worktrees de agente nascem do commit em que a sessão começou, não do `main` atual.** Na E2, as três branches saíram de `13fb0d0` mesmo com a `main` já adiante. A BE-4 percebeu e fez `git rebase main` antes de codar. **Ao lançar uma frente que depende de merge anterior, instrua explicitamente a rebasear e a confirmar a base.**
2. **Agentes longos caem por infraestrutura.** Na E2 houve um stall de watchdog (600s) e uma queda de API mid-response, ambas sem perda porque o trabalho foi commitado como checkpoint e as frentes foram retomadas do transcript. **Instrua commits incrementais desde o início.**
3. **O update gerado pelo orval é `PATCH`, não `PUT`.** Handler de teste com `http.put` falha em silêncio: o setup usa `onUnhandledRequest: "bypass"` e a requisição vaza para a rede real.
4. **`queryClient.setQueryData(key, undefined)` é no-op no TanStack v5** — use `removeQueries`.
5. **Mocks do orval respondem faker aleatório com 1s de delay**, inúteis para asserção. Use `src/test/financeFixtures.ts` e sobreponha com `server.use(...)`.

## 8. A Etapa E3 — três frentes paralelas

Fichas completas em `DESENVOLVIMENTO-MVP.md` §4. Rode isoladas em git worktrees.

- **BE-5 — Dashboard, Alocação por pilar & Export** `[spring-boot-engineer]` — reserva Flyway V12. Regra 10: alocação por competência (compra de cartão conta na data da compra, não do pagamento), pagamento de fatura fora dos gastos por categoria (R2), transferências fora de tudo, aportes somam no pilar (R9). Mês sem receita → percentuais nulos + `noIncome`.
- **FE-3 — Telas de cartão e fatura** `[react-specialist]` — ⚠️ depende de §6.2 (arquivar cartão) para a tela de cartões ficar completa.
- **FE-4 — Dashboard** `[react-specialist]` — adiciona Recharts; ver §6.8.

**A passada de contrato já foi feita** (§6.1–6.4 e §6.6 resolvidos). As três frentes podem ser lançadas em paralelo imediatamente.

**Itens de UI/refino que sobraram e cabem nas fichas da E3:**
- **FE-3** (ou ajuste pontual em `features/transactions/`): reexibir o botão de editar em linhas TRANSFER — contrato e backend destravados, falta só a UI. Os hooks `useUpdateTransfer` e `useArchiveCard` já existem gerados.
- **FE-3**: UI de estorno de pagamento (`useReverseStatementPayment` + array `payments` já gerados), tradução de `STATEMENT_PAYMENT_NOT_FOUND`, e revisar a redação de `STATEMENT_IMMUTABLE` (§6.5).
- ~~**BE-5**: aposentar o `TransferUpdateHandler`~~ — ✅ **FEITO (22/07/2026)**, fora do fluxo de frente. `TransferService.update` passou a receber um `TransferUpdateInput` dedicado (com `contributionPillar` de primeira classe), faz o próprio lookup pelo repositório (preservando a não-dependência de `TransactionService`) e resolve o pilar pela regra única `resolveContributionPillar`. O `PATCH /transactions` faz ponte para um `TransferUpdateInput` que mantém o pilar. Handler deletado; 175 testes verdes, nenhum reescrito; sem mudança de contrato/migração/front.
- **FE-4**: `manualChunks` ou import dinâmico ao adicionar Recharts (§6.8).

## 9. Gate da E3

```bash
cd mk-finance/api && ./mvnw verify        # exige Docker
cd mk-finance/web && npm test && npm run build
POSTGRES_PASSWORD=x docker compose -f mk-finance/docker/docker-compose.yml config -q
```
Gate específico: percentuais de alocação conferidos **manualmente contra planilha** do cenário canônico (salário + compras de cartão + aporte + transferência).
