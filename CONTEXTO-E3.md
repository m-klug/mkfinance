# Contexto para execução da E3 — MK Finance

> **Leia este arquivo primeiro.** Handoff completo para iniciar a Etapa E3 num chat novo.
> Detalhamento: `PLANEJAMENTO.md` (fonte da verdade de negócio) e `DESENVOLVIMENTO-MVP.md` (fichas e convenções).
> Atualizado em 22/07/2026, após a E2 concluída. Substitui o `CONTEXTO-E2.md`.

## 1. O projeto

App de controle financeiro pessoal, **self-hosted** (Proxmox + LXC, acesso via WireGuard e DNS interno). Uso pessoal, mas modelado como **multiusuário por espaço (`Workspace`)** desde o início. Organiza despesas pelo **método dos 6 pilares** com alvos fixos no MVP: Custos Fixos 30, Conforto 10, Metas 20, Prazeres 10, Liberdade Financeira 25, Conhecimento 5.

Stack e layout do repositório: inalterados desde a E2 — ver `CONTEXTO-E2.md` §2.

## 2. Estado atual: E0, E1 e E2 concluídas (gate verde)

Gate da E2 rodado sobre a `main` já integrada:

| Eixo | Resultado |
|---|---|
| `./mvnw verify` | **151 testes, 0 falhas, BUILD SUCCESS** |
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

### 6.1 Contrato: `transferDirection` ausente (levantada pela BE-3)
O schema `Transaction` não expressa a direção da transferência e `amountCents` tem `minimum: 1`, então o front **não consegue distinguir entrada de saída** no extrato. Correção sugerida, aditiva e não-quebra: `transferDirection: { enum: [OUT, IN], nullable: true }`. O backend já persiste o dado (V7); faltaria só mapear.

### 6.2 Contrato: não há como arquivar um cartão (levantada pela BE-4)
`Card.archived` existe e `listCards?includeArchived` existe, mas `CardRequest` não tem o campo e não há endpoint `/archive` (contas têm). Hoje `archived` é **inalcançável pela API**. Sugestão: `POST /cards/{cardId}/archive`, espelhando contas. **A FE-3 vai bater nisso.**

### 6.3 Contrato: códigos de erro a sincronizar
A BE-4 adicionou `CARD_NOT_FOUND`, `CARD_ARCHIVED`, `PURCHASE_NOT_FOUND`, `STATEMENT_NOT_FOUND`, `STATEMENT_EMPTY`. A lista do contrato diz "include", então não é quebra — mas vale sincronizar.

### 6.4 `AccountResponse.balanceCents` ainda retorna `null`
O `AccountBalanceService` está pronto e testado, mas expô-lo exige editar `api/account/AccountMapper` e `application/account/AccountService` — **arquivos da BE-2**. Ambas as frentes de backend se recusaram a invadir a fatia alheia, corretamente. Mudança de ~5 linhas: injetar o serviço, chamar `balancesByAccount(workspaceId)` uma vez no endpoint de lista, passar cada `Balance` ao mapper.
Não bloqueia o front (que trabalha contra mocks e já tem fallback isolado em `effectiveBalanceCents`), **mas bloqueia a INT-1**.

### 6.5 Sem endpoint de estorno de pagamento de fatura
Excluir pelo `/transactions` a despesa gerada por um pagamento é bloqueado pelo FK de `statement_payment.transaction_id` → 409 `DATA_INTEGRITY_VIOLATION`. Protetivo e aceitável, mas o código de erro é genérico e não há caminho de estorno no contrato. Decisão de produto.

### 6.6 Transferência não é editável no extrato
Consequência do contrato, que só aceita payload INCOME/EXPENSE no update; editar um lado só corromperia o par espelhado. A FE-2 escondeu o editar em linhas TRANSFER — correção é excluir e relançar. Candidata a revisão na INT-1.

### 6.7 Selects nativos em vez do `Select` do shadcn
A FE-2 usou `<select>` estilizado nos forms (picker do SO no mobile, acessibilidade de graça, evita stubs de Radix no jsdom). Cria inconsistência visual com o design system; o componente do shadcn segue intocado e a reversão é possível.

### 6.8 Bundle cruzou 500 kB
O build agora emite aviso de chunk >500 kB (535,98 kB / 164,86 kB gzip). **É novo da E2** — no baseline da E1 eram 483,66 kB sem aviso. Não é erro, é dica de code-splitting. A FE-4 vai adicionar Recharts e piorar isso: considere `manualChunks` ou import dinâmico.

### 6.9 Pendências herdadas da E2, ainda válidas
- **Integração real** front↔API só na INT-1. Pontos a acertar: `context-path: /api` no backend e o fluxo CSRF (GET inicial → cookie `XSRF-TOKEN` → ecoar em `X-XSRF-TOKEN`).
- **Visibilidade do repo no GitHub** não verificada (`gh` CLI não instalado).

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

**Ordem sugerida**: aplicar os patches de contrato (§6.1, §6.2, §6.3) com o `java-architect` **antes** de lançar as frentes, já que nenhuma outra frente está rodando agora e mudar o contrato sob um front em execução quebraria os mocks dele. Depois `npm run api:generate` e as três frentes em paralelo.

## 9. Gate da E3

```bash
cd mk-finance/api && ./mvnw verify        # exige Docker
cd mk-finance/web && npm test && npm run build
POSTGRES_PASSWORD=x docker compose -f mk-finance/docker/docker-compose.yml config -q
```
Gate específico: percentuais de alocação conferidos **manualmente contra planilha** do cenário canônico (salário + compras de cartão + aporte + transferência).
