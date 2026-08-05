# Plano de Desenvolvimento da Fase 2 — MK Finance

> Plano operacional para execução **multi-agente** da Fase 2 definida em `PLANEJAMENTO.md` §7.
> Gerado em 30/07/2026, com o MVP completo e a stack já rodando self-hosted em Docker/HTTPS.
> Sucede o `DESENVOLVIMENTO-MVP.md` (E0–E4), que permanece como registro do MVP.

---

## 1. Como executar este plano com agentes

### 1.1 Personas (skills em `.claude/agents/`)

| Persona | Responsabilidade nesta fase |
|---|---|
| `java-architect` | Contrato OpenAPI (CT-1), decisões de schema e a integração final (INT-2) |
| `spring-boot-engineer` | Backend: BE-6, BE-7, BE-8 |
| `react-specialist` | Frontend: FE-5, FE-6, FE-7 |

Cada ficha da seção 4 é executada por **um agente**, com o prompt contendo: a ficha completa, as convenções da seção 2, as armadilhas da seção 6 e a instrução de ler `PLANEJAMENTO.md` + `mk-finance/contracts/openapi.yaml` antes de codar.

### 1.2 Regras de sincronização

1. **O contrato é o ponto único de sincronização.** `mk-finance/contracts/openapi.yaml` define a API. O front **não espera o back** — gera cliente tipado + mocks MSW (`npm run api:generate`) e desenvolve contra os mocks. A integração real acontece na INT-2.
2. **Mudança de contrato é exclusiva do `java-architect`.** Por isso a CT-1 roda **sozinha e primeiro**: com o contrato fechado, as três frentes de back e as três de front correm em paralelo sem se bloquear. Se um workstream descobrir que precisa de mudança no contrato, ele PARA e registra o pedido — não edita.
3. **Propriedade exclusiva de pastas** — um agente nunca edita pasta de outro. Backend: cada BE-x é dono dos pacotes listados na sua ficha. Frontend: FE-x é dono de `web/src/features/<área>/`; `components/ui/`, `lib/` e `i18n/` são infra compartilhada (alteração vira pedido, não edição direta — exceto acrescentar chaves i18n do próprio grupo).
4. **Migrações Flyway: append-only, e nesta fase começando em V13** — ver o aviso da seção 3.
5. **Definition of Done**: código + testes passando localmente (`./mvnw verify` no back, requer Docker; `npm test && npm run build` no front) + chaves i18n adicionadas + zero warning novo de compilação.
6. **Commits incrementais desde o início** — agentes longos caem (infra ou limite de sessão); commit incremental salva o trabalho e `SendMessage` ao agentId retoma do transcript.

### 1.3 Ordem de execução e paralelismo

```
E5: CT-1 ──┬─ E6: BE-6 ─┬─ E7: BE-8 ─┬─ E8: INT-2
(contrato) ├─ E6: BE-7 ─┼─ E7: FE-6 ─┤  (sequencial)
           └─ E6: FE-5 ─┴─ E7: FE-7 ─┘
```

Dentro de cada etapa as frentes rodam **em paralelo**. Uma etapa só começa quando o gate da anterior passou (seção 5).

---

## 2. Convenções obrigatórias (todas as fichas herdam)

Herdadas do MVP e **inalteradas**:

- **Fonte da verdade de negócio**: `PLANEJAMENTO.md`. Em conflito, o PLANEJAMENTO vence e a divergência é registrada.
- **Dinheiro**: sempre `long` em **centavos**. Nunca float/double/BigDecimal no domínio.
- **Datas de negócio**: `LocalDate` (coluna `DATE`), referência `America/Sao_Paulo` via `BusinessClock`. `TIMESTAMPTZ` só para auditoria.
- **Isolamento por workspace** (regra 8): toda query filtra por `workspace_id`; a membership é resolvida uma vez por request pelo `WorkspaceAccessInterceptor` + `WorkspaceContext` (`@RequestScope`) — **nunca reimplementar**.
- **Erros da API**: código de máquina em `ErrorResponse{code, params?, fieldErrors?}`; o front traduz. Backend nunca devolve texto pt-BR. Constante em `domain/exception/ErrorCode.java` + factory em `AppException`.
- **i18n**: zero string literal em JSX; chaves em `web/src/i18n/locales/pt-BR/common.json`.
- **Lombok** nas entidades JPA, **`record`** nos DTOs.
- **Selects nativos `<select>`** via `components/form/fields.tsx` — não introduzir o `Select` do shadcn.
- **Layout backend**: `domain/` (model, service puro, exception), `application/` (services transacionais), `api/` (controller, dto, mapper), `infrastructure/`.

Confirmadas na leitura do código atual, e que valem como convenção explícita daqui em diante:

- **Sem relacionamento JPA** — FKs são UUID escalares, para que toda query filtre `workspaceId` explicitamente e nada faça lazy-load.
- **Regra de negócio pura vive em `domain/service/`** como classe final estática, testável sem Spring (`PillarAllocationCalculator`, `BalanceCalculator`, `InstallmentSplitter`, `StatementAllocator`).
- **Testes de back**: `*IntegrationTest` estendendo `AbstractIntegrationTest` (Testcontainers + `TestRestTemplate` + `ApiSession`, exercitando a cadeia real de segurança/CSRF); unidade pura em `<Classe>Test` com `@Nested` nomeado pela regra.
- **Testes de front**: co-locados `<Nome>.test.tsx`, MSW por cima de fixtures determinísticas (`financeFixtures`/`dashboardFixtures` — os mocks gerados pelo orval são faker aleatório com 1s de delay e não servem para asserção), `renderWithProviders` + `seedAuthenticatedSession()`.
- **Nunca editar `web/src/lib/api/generated/**`** — o orval roda com `clean: true` e apaga a árvore inteira a cada geração.

---

## 3. Etapas

| Etapa | Frentes | Persona | Pré-requisito |
|---|---|---|---|
| **E5 — Contrato** | CT-1 (sequencial) | java-architect | MVP entregue |
| **E6 — Pilares & Recorrências** | BE-6 · BE-7 · FE-5 | spring-boot-engineer ×2, react-specialist | Gate E5 |
| **E7 — Tags, Calendário & Séries** | BE-8 · FE-6 · FE-7 | spring-boot-engineer, react-specialist ×2 | Gate E6 |
| **E8 — Integração** | INT-2 (sequencial) | java-architect | Gate E7 |

### ⚠️ Reserva Flyway — esta fase começa em V13

**BE-6 → V13 · BE-7 → V14–V15 · BE-8 → V16–V17.**

O `DESENVOLVIMENTO-MVP.md` §67 registra que V3–V6, V8 e V10–V12 ficaram **vagas**. Isso continua valendo como *"não renumere as existentes"*, mas **não** como *"reaproveite os buracos"*:

`spring.flyway.out-of-order` **não está habilitado** (`api/src/main/resources/application.yaml`) e agora existe **banco de produção já migrado**. Uma migração nova com versão menor que a máxima aplicada faz o Flyway falhar a validação no boot. Portanto: **sempre V13 ou maior**. Os buracos permanecem buracos, para sempre.

Lembrete relacionado: `jpa.hibernate.ddl-auto: validate` — todo campo novo de entidade exige migração correspondente, senão a aplicação não sobe.

---

## 4. Fichas de tarefa

### CT-1 — Contrato da Fase 2 `[E5 · java-architect, sequencial]`

- **Objetivo**: fechar em `mk-finance/contracts/openapi.yaml` toda a superfície de API da Fase 2, destravando back e front em paralelo.
- **Dono de**: `mk-finance/contracts/openapi.yaml`, `domain/exception/ErrorCode.java`, `domain/exception/AppException.java`.
- **Escopo**:
  - `PUT /workspaces/{workspaceId}/pillar-allocations` — corpo com os 6 pilares e seus `targetPercent`; soma obrigatória de 100.
  - `GET /workspaces/{workspaceId}/pillar-allocations` — acrescentar query param `month` (`yyyy-MM`, opcional, default = mês corrente), devolvendo os alvos **vigentes naquela competência**. Remover do contrato o comentário "Fixed targets in the MVP… editing arrives in Phase 2".
  - Tag `recurrences` — CRUD de regras; `PATCH`/`DELETE` aceitam `scope` (`THIS_ONE` | `THIS_AND_FUTURE`).
  - `GET /workspaces/{workspaceId}/calendar?month=yyyy-MM` — agregação **somente leitura**.
  - Tag `tags` — CRUD mínimo; campo `tags` em `TransactionRequest`/`TransactionResponse`; filtro `tags` em `GET /transactions`.
  - Novos códigos de erro: `PILLAR_TARGETS_MUST_SUM_100`, `RECURRENCE_NOT_FOUND`, `RECURRENCE_OCCURRENCE_NOT_FOUND`, `TAG_NAME_DUPLICATE`.
- **Aceite**: `npm run api:generate` roda limpo e produz hooks + mocks para as tags novas; nenhum endpoint existente quebra de forma incompatível.

#### ✅ CT-1 concluída — decisões de schema fechadas (vinculantes para as fichas seguintes)

Executada em 30/07/2026. Verificado: `api:generate` limpo, `npm run build` ok, 74 testes verdes, `./mvnw compile` ok. O que o contrato decidiu além do que a ficha enunciava:

1. **O calendário tem duas espécies de entrada, não três** — `STATEMENT_DUE` e `PENDING_TRANSACTION`. Como a materialização é lazy na leitura do mês, toda ocorrência de recorrência daquele mês **já é** uma transação pendente; listá-la também como "ocorrência" dobraria o mês. Quem nasceu de série carrega `recurrenceId`, e é assim que a FE-6 diferencia (badge, "editar a série"). `Transaction` também ganhou `recurrenceId` pelo mesmo motivo, para o extrato.
2. **Agendamento explícito na regra**, não derivado da `startDate`: `dayOfMonth` (MONTHLY/YEARLY), `dayOfWeek` (WEEKLY), `monthOfYear` (YEARLY). Mudar o dia do aluguel não deve mexer na data de início da série. A obrigatoriedade condicional por frequência o OpenAPI não expressa — **valida no servidor e responde `VALIDATION_ERROR` com `fieldErrors`**, sem código de erro novo (a BE-7 não inventa códigos fora dos 4 da ficha).
3. **`occurrenceDate` é o pivô do `scope`** em `PATCH`/`DELETE` de recorrência: obrigatório em `THIS_ONE` (é o que justifica `RECURRENCE_OCCURRENCE_NOT_FOUND`); em `THIS_AND_FUTURE` o default é a próxima ocorrência a partir de hoje, para o cliente não ter que calcular data.
4. **Tag é o seu nome.** `TransactionRequest.tags` recebe **nomes** (o que não existe é criado na hora — é o que faz o autocomplete de texto livre funcionar sem round-trip); `Transaction.tags` devolve **objetos** `{id, name}`; o filtro `tags` do `GET /transactions` também é por nome, OR, case-insensitive. `tags` é **opcional** na resposta de propósito: torná-lo obrigatório quebraria a tipagem das fixtures existentes na hora. O servidor sempre manda (vazio quando não há).
5. **Alvos de pilar usam `PUT`, não `PATCH`** — os seis alvos são um conjunto indivisível, só significam algo somando 100. ⚠️ **FE-5: o handler MSW é `http.put`** — a armadilha 5 da seção 6 vale ao contrário aqui, e `onUnhandledRequest: "bypass"` faria o teste falhar em silêncio.
6. **`PillarAllocation.effectiveFrom`** (opcional) na resposta, para a tela dizer desde quando o número vale.

Aberto para a BE-6/BE-8, sem impacto de contrato: `GET /export/transactions.csv` continua declarando só `from`/`to` embora a descrição prometa "os mesmos filtros de listTransactions" — divergência **pré-existente**, não introduzida aqui.

### BE-6 — Alocação: status de agregação + alvos versionados `[E6 · spring-boot-engineer]` (V13)

- **Objetivo**: tornar os percentuais-alvo editáveis e versionados por competência, e corrigir a agregação do dashboard para refletir o **realizado**.
- **Entradas**: PLANEJAMENTO §6.4, §6.6, §5 regras 2, 9, 10.
- **Dono de**: `application/dashboard/**`, `application/category/PillarAllocationService`, `api/pillar/**`, `domain/model/{PillarAllocation,PillarAllocationId}`, migração V13.

**A. Correção — a alocação passa a contar só `SETTLED`**

Hoje **nenhuma** das queries de `application/dashboard/DashboardAggregateRepository.java` filtra por status, então uma transação `PENDING` já entra na alocação com 100% do valor. Como a BE-7 vai gerar pendentes em massa, sem esta correção o mês inteiro apareceria como gasto no dia 1º.

- Acrescentar o predicado de status em `sumIncome`, `expenseTransactionsByCategory` e `contributionsByPillar`.
- `cardPurchasesByCategory` **não muda** — `card_purchase` não tem status, e a compra conta pela data da compra (regra 2).
- Isso alinha a alocação à regra 5, que já separa saldo **atual** (SETTLED) de **projetado** (SETTLED + PENDING) em `BalanceCalculator`.
- Teste de regressão obrigatório: uma transação `PENDING` no mês **não** move nenhum percentual do relatório.

**B. Versionamento por competência (V13)**

- A tabela `pillar_allocation` já existe por workspace (PK `workspace_id, pillar`). A migração acrescenta `effective_from DATE NOT NULL`, move a PK para a tripla e faz **backfill das linhas existentes com uma data-piso** (as alocações semeadas valem desde sempre).
- Ajustar o `@EmbeddedId` `PillarAllocationId`.
- Manter o `CHECK (target_percent BETWEEN 0 AND 100)` por linha; a soma = 100 é regra de aplicação, não de banco (o banco não consegue validar o conjunto).

**C. Escrita**

- `PUT` valida soma = 100 → 400 `PILLAR_TARGETS_MUST_SUM_100`.
- Grava com `effective_from` = **1º dia do mês corrente** (decisão do usuário: a edição vale do mês corrente em diante). Se já houver linhas dessa competência, atualiza; senão insere — o histórico anterior nunca é tocado.

**D. Leitura**

- `DashboardService.targets(UUID)` vira `targets(UUID, YearMonth)`, resolvendo a linha de `max(effective_from) <= primeiro dia do mês`.
- **É o único seam do motor**: `PillarAllocationCalculator.allocate(...)` já recebe `Map<Pillar,Integer>` por parâmetro e **não muda**.
- `PillarAllocationController` (GET) passa a aceitar `month`.

- **Aceite**: cenário canônico da E3 segue verde; editar os alvos em julho não altera o relatório de junho; soma ≠ 100 → 400; PENDING não move a alocação.

### BE-7 — Recorrências `[E6 · spring-boot-engineer]` (V14–V15)

- **Objetivo**: séries recorrentes (salário, aluguel, assinaturas) que materializam ocorrências como transações **PENDING**, confirmadas em um clique.
- **Entradas**: PLANEJAMENTO §7 Fase 2; regra 5 (pendente/projetado).
- **Dono de**: `api/recurrence/**`, `application/recurrence/`, `domain/model/{RecurrenceRule,RecurrenceOccurrence,RecurrenceFrequency}`, migrações V14–V15.
- **Modelo**:
  - `recurrence_rule` — workspace, tipo, conta, categoria, valor, descrição, `frequency` (`MONTHLY` | `WEEKLY` | `YEARLY`), dia de referência, `start_date`, `end_date` (nulo = sem fim), `active`.
  - `recurrence_occurrence(rule_id, occurrence_date, status GENERATED|SKIPPED, transaction_id nullable)` — **é o que garante idempotência** da materialização e permite registrar "pular esta ocorrência" sem existir transação. Unique em `(rule_id, occurrence_date)`.
- **Materialização**: **lazy por competência**, reaproveitando o padrão já usado para faturas (statements criados on-demand por `(card, reference_month)`). Ao ler o mês (extrato ou calendário), as ocorrências faltantes daquela competência nascem como transações `PENDING`.
- **Confirmar**: reusa `POST /workspaces/{id}/transactions/{id}/settle`, que **já existe**. Nenhum endpoint novo de efetivação.
- **Editar/excluir com escopo**:
  - `THIS_ONE` — afeta só aquela ocorrência (edita a transação materializada, ou marca `SKIPPED`).
  - `THIS_AND_FUTURE` — fecha a regra vigente (`end_date` = véspera da ocorrência) e cria uma nova regra a partir dali com os valores novos. Padrão clássico de calendário; preserva o passado intacto.
- **Regras críticas**: frequência mensal em dia inexistente (31 em fevereiro) cai no último dia do mês; desativar a regra não apaga ocorrências já materializadas; excluir a regra não apaga transações já efetivadas.
- **Aceite**: testes de unidade da geração de datas (dia 31, ano bissexto, semanal cruzando mês, anual); materializar o mesmo mês duas vezes **não** duplica transações; `THIS_AND_FUTURE` não altera ocorrências passadas.

#### ✅ E6 concluída (BE-6 · BE-7 · FE-5) — 30/07/2026

Gate verde na `main` com as três frentes integradas: `./mvnw clean verify` 237 testes, `npm test` 79, `npm run build` e `docker compose config` ok. Reservas de Flyway respeitadas (V13 · V14–V15), zero sobreposição de arquivo entre as frentes de back.

**⚠️ Pendência real herdada da BE-7 — a materialização NÃO está ligada ao extrato.**

A ficha dizia "ao ler o mês (extrato ou calendário), as ocorrências faltantes nascem". A BE-7 ligou a materialização **só ao endpoint do calendário**, com uma justificativa boa: o `GET /transactions` filtra por `from`/`to`, um intervalo arbitrário sem noção de competência, e materializar sob um range ilimitado é exatamente o "escrever linhas até 2094" que o contrato evita. `TransactionController`/`TransactionService` também estavam fora da propriedade dela, então parar foi o comportamento certo.

**Consequência de produto**: hoje uma recorrência só vira transação pendente depois que alguém abre o calendário daquele mês. Quem for direto ao extrato não vê o salário do mês.

**✅ RESOLVIDO — decisão do usuário em 30/07/2026: o extrato materializa por competência.** O `GET /transactions` passa a materializar as competências que a janela `from`/`to` tocar, **exigindo janela limitada**; consulta sem janela (ou com janela absurda) não materializa nada, que é como se evita o "escrever linhas até 2094". A recorrência passa a aparecer onde quer que o usuário olhe, cumprindo o que a Fase 2 prometeu: a ocorrência nasce como pendente para confirmar com 1 clique.

**Alocação**: este trabalho vai para a **BE-8**, não para uma ficha nova. Motivo de engenharia, não de conveniência — ele mexe em `TransactionService`/`TransactionSpecifications`, exatamente os arquivos que a BE-8 já toca para o filtro de tags. Duas fichas paralelas ali brigariam pelo mesmo arquivo. **Sem mudança de contrato**: materializar na leitura é mudança de comportamento, não de forma. Em particular, **não** mexa nos defaults de `from`/`to` do `listTransactions` — isso sim seria mudança de contrato e quebraria a listagem existente.

Outras duas decisões registradas, sem impacto de contrato: fechar regra por `THIS_AND_FUTURE` marca `active=false` além de gravar `end_date`; e payload com seis itens mas pilar duplicado/ausente responde `400 VALIDATION_ERROR` genérico, sem código de erro novo.

### BE-8 — Tags `[E7 · spring-boot-engineer]` (V16–V17)

- **Objetivo**: tags livres e múltiplas por transação, filtráveis no extrato.
- **Dono de**: `api/tag/**`, `application/tag/`, `domain/model/Tag`, migrações V16–V17, e o predicado novo em `TransactionSpecifications`.
- **Modelo**: `tag(id, workspace_id, name)` com unicidade **case-insensitive** por workspace; `transaction_tag(transaction_id, tag_id)` com PK composta.
- **Escopo deliberado**: tags só em `transaction`. `card_purchase`, `statement` e `installment` **não têm `workspace_id` próprio** (são escopados transitivamente pelo cartão) — incluí-los custaria bem mais e fica fora desta fase.
- **Filtro**: parâmetro `tags` no `GET /transactions`, semântica **"qualquer uma das tags"** (OR), somando-se aos filtros existentes. O predicado de workspace continua entrando primeiro e incondicionalmente em `TransactionSpecifications`.
- **Export**: acrescentar coluna de tags ao `TransactionCsvExporter` (manter UTF-8 BOM e separador `;`).
- **Aceite**: criar tag duplicada só diferindo por caixa → 409 `TAG_NAME_DUPLICATE`; filtro por 2 tags devolve a união; excluir tag não apaga transações; CSV sai com a coluna nova.

### FE-5 — Configurações de pilares `[E6 · react-specialist]`

- **Objetivo**: tela para editar os percentuais-alvo, com validação de soma ao vivo.
- **Dono de**: `web/src/features/settings/**`.
- **Passos**: nova rota `/settings/pillars` no bloco `<AppShell>` de `src/App.tsx` + item em `NAV_ITEMS` (`components/layout/AppShell.tsx`) + chave `nav.settings`.
- **Reaproveitar**: o hook `useListPillarAllocations` **já está gerado e sem nenhum uso** — é exatamente a leitura desta tela.
- **UX**: os 6 pilares em campos numéricos, indicador de soma sempre visível, botão salvar desabilitado enquanto a soma ≠ 100 (a validação do servidor continua sendo a autoridade), aviso de que a mudança vale do mês corrente em diante.
- **i18n**: grupo `settings.pillars.*`. Rótulos de pilar reusam `pillar.*`, que já existe.
- **Aceite**: soma ≠ 100 bloqueia o envio e explica o motivo; erro do servidor cai em `translateApiError`; teste cobrindo edição válida e inválida.

### FE-6 — Recorrências & Calendário `[E7 · react-specialist]`

- **Objetivo**: gerenciar séries recorrentes e visualizar o mês.
- **Dono de**: `web/src/features/recurrences/**`, `web/src/features/calendar/**`.
- **Recorrências** (`/recurrences`): lista + dialog de criação/edição no padrão de `AccountFormDialog` (estado único + erro por campo + `<form noValidate>`); ao editar ou excluir uma série já materializada, um diálogo pergunta o escopo (**só esta** / **esta e as futuras**).
- **Calendário** (`/calendar`): visão de mês, **somente leitura** — vencimentos de fatura, transações pendentes e ocorrências de recorrência. Sem confirmar/pagar pela agenda (decisão do usuário); notificações são Fase 3.
- **i18n**: grupos `recurrences.*` e `calendar.*`; frequências como mapa enum→rótulo (`recurrenceFrequency.MONTHLY`…), seguindo a convenção de `transactionStatus.*`.
- **Aceite**: criar série mensal e ver as ocorrências no calendário; o diálogo de escopo aparece só quando faz sentido; responsivo no celular; testes dos dois fluxos contra fixtures MSW.

### FE-7 — Tags no extrato `[E7 · react-specialist]`

- **Objetivo**: atribuir e filtrar tags.
- **Dono de**: `web/src/features/transactions/**` (tags entram no form e no filtro existentes).
- **Passos**: entrada de tags em `TransactionFormDialog` e `QuickTransactionDialog` (com autocomplete das tags já existentes); filtro em `TransactionsPage`; exibição das tags na linha da tabela.
- **⚠️ Nota de layout**: a grade de filtros já está **cheia** — `grid sm:grid-cols-2 lg:grid-cols-4` com a busca ocupando 2 colunas. Acrescentar o filtro de tags exige **rearranjar a grade**, não só empilhar mais um campo.
- **Atenção ao lançamento rápido**: o `QuickTransactionDialog` tem meta de ≤5 interações — tags são opcionais e não podem entrar no caminho crítico.
- **i18n**: grupo `tags.*` + chaves de filtro em `transactions.*`.
- **Aceite**: criar transação com 2 tags; filtrar por tag reduz a lista; limpar o filtro restaura; testes cobrindo atribuição e filtro.

### INT-2 — Integração & gate da Fase 2 `[E8 · java-architect, sequencial]`

- **Objetivo**: fechar a fase contra a stack real já implantada.
- **Passos**: `npm run api:generate` e conferência contrato × springdoc `/v3/api-docs`; rodar o gate completo (seção 5); estender `web/e2e/smoke.spec.ts` com editar alvos → confirmar recorrência → filtrar por tag; conferir i18n sem chave faltando; validar as migrações V13+ contra um **dump restaurado do banco de produção** (é o único jeito de provar que o backfill do `effective_from` e a troca de PK funcionam com dados reais).
- **Aceite**: gate verde; E2E verde; migrações aplicam limpo sobre o dump de produção; dashboard do mês corrente confere após a mudança para SETTLED-only.

---

## 5. Gates de verificação entre etapas

```bash
cd mk-finance/api && ./mvnw verify          # exige Docker (Testcontainers)
cd mk-finance/web && npm test && npm run build
POSTGRES_PASSWORD=x docker compose -f mk-finance/docker/docker-compose.yml config -q
```

Gate adicional por etapa — **E5**: `npm run api:generate` limpo, sem edição manual em `generated/**`. **E6**: cenário canônico da E3 reconferido à mão após a mudança para SETTLED-only + edição de alvo não retroage. **E7**: materializar o mesmo mês duas vezes não duplica nada. **E8**: aceite do INT-2.

---

## 6. Armadilhas operacionais (herdadas do `CONTEXTO-E4.md` §8)

1. **Worktrees de agente nascem do commit em que a sessão começou**, não do `main` atual — instrua `git rebase main` explícito antes de codar.
2. **Agentes longos caem** (infra ou limite de sessão da conta). Commit incremental salva; `SendMessage` ao agentId retoma do transcript.
3. **Após mergear branch que adiciona deps npm, rode `npm install` no checkout principal ANTES do gate** — o `node_modules` do worktree mascara a falta e o `main` quebra com `ERR_MODULE_NOT_FOUND`.
4. **Após merge de duas frentes de front, rode `npm run build`, não só `npm test`** — o 3-way pode deixar import órfão que só o `tsc` estrito pega.
5. **orval gera `PATCH`, não `PUT`** para updates — handler de teste com `http.put` falha em silêncio (`onUnhandledRequest: "bypass"`).
6. **`queryClient.setQueryData(key, undefined)` é no-op no TanStack v5** — use `removeQueries`.
7. **`git push` está bloqueado** pela trava em `.claude/settings.json` — só o usuário consegue.

---

## 7. Fora do escopo desta fase

**Caixinhas — adiadas para uma fatia própria.** São o item mais pesado da Fase 2 no roadmap porque **reestruturam o motor de alocação**: o pilar do aporte passaria a vir da caixinha de destino, refinando a regra 9 e mexendo em `contributionsByPillar` e no `PillarAllocationCalculator`. Merecem planejamento dedicado, depois que os alvos versionados da BE-6 estiverem estáveis — as duas coisas tocam o mesmo motor e não devem andar juntas.

Também fora: orçamento **por categoria** (decisão do usuário — o controle fica no nível de pilar); rollover de sobra/estouro entre meses; notificações (Fase 3).

---

## 8. Notas incidentais úteis

- `web/src/components/ui/select.tsx` (shadcn/Radix) existe mas tem **zero imports** — arquivo morto. Use `SelectField` de `components/form/fields.tsx`, construído sobre `<select>` nativo.
- A tag `export` (CSV) é gerada pelo orval mas **não tem nenhuma UI** — o backend exporta e ninguém chama. Oportunidade barata, fora do escopo desta fase.
- Os filtros do extrato **não vivem na URL** (sem `searchParams`) e a busca textual **não tem debounce** — cada tecla dispara query. Relevante se a FE-7 mexer nessa área.
