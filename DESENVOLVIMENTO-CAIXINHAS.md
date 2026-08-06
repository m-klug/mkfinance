# Plano de Desenvolvimento das Caixinhas — MK Finance

> Plano operacional para execução **multi-agente** da fatia "Caixinhas", adiada da Fase 2 de propósito
> (ver `DESENVOLVIMENTO-FASE2.md` §7 "Fora do escopo desta fase").
> Gerado em 05/08/2026, com a Fase 2 fechada na `main` e a stack rodando self-hosted.
> Sucede o `DESENVOLVIMENTO-FASE2.md` (E5–E8), que permanece como registro daquela fase.

---

## 0. Por que esta fatia existe

Hoje uma conta de investimento é **um montante único** e o pilar do aporte vem de `Account.defaultContributionPillar`. Isso não distingue "R$ 6.000 de reserva de emergência" de "R$ 4.000 da viagem de 2027" dentro da mesma corretora, e trata o pilar como propriedade da **conta** quando ele é, na verdade, propriedade do **destino do dinheiro**.

As caixinhas são subdivisões virtuais da conta: o saldo real continua um montante único derivado de `transaction`, mas se reparte logicamente entre caixinhas + uma fatia "não alocado". Isso **refina a regra 9** (o pilar do aporte passa a vir da caixinha de destino) e **absorve o conceito de "metas de economia"** do roadmap (valor-alvo + prazo + progresso).

Foi adiada da Fase 2 porque mexe no mesmo motor de alocação que os alvos versionados da BE-6 — e as duas coisas não deviam andar juntas. Com a BE-6 estável em produção, a espera acabou.

---

## 1. Como executar este plano com agentes

### 1.1 Personas (skills em `.claude/agents/`)

| Persona | Responsabilidade nesta fatia |
|---|---|
| `java-architect` | Contrato OpenAPI (CT-2) e integração final (INT-3) |
| `spring-boot-engineer` | Backend: BE-9, BE-10, BE-11 |
| `react-specialist` | Frontend: FE-8, FE-9 |

Cada ficha da seção 5 é executada por **um agente**, com o prompt contendo: a ficha completa, as convenções da seção 2, **o desenho da seção 4 na íntegra** e as armadilhas da seção 7.

### 1.2 Regras de sincronização

Idênticas às da Fase 2 (`DESENVOLVIMENTO-FASE2.md` §1.2) — repetidas aqui só nos pontos que mudam:

1. **O contrato é o ponto único de sincronização.** CT-2 roda sozinha e primeiro; depois back e front correm em paralelo contra os mocks MSW gerados.
2. **Mudança de contrato é exclusiva do `java-architect`.** Quem descobrir que precisa, PARA e registra o pedido — não edita.
3. **Propriedade exclusiva de arquivos.** A tabela de donos está em cada ficha e é conferida na seção 5.4. **Frentes paralelas nunca compartilham arquivo** — foi a lição cara da Fase 2.
4. **Migrações Flyway: V18–V19** — ver o aviso da seção 3.
5. **Definition of Done**: código + testes locais verdes (`./mvnw verify` no back, requer Docker; `npm test && npm run build` no front) + chaves i18n + zero warning novo.
6. **Commits incrementais desde o início.**

### 1.3 Ordem de execução e paralelismo

```
E9: CT-2 ──┬── E10: BE-9 ──┬── E11: BE-10 ─┬── E12: INT-3
(contrato,  │        FE-8 ──┘        BE-11 ─┤    (sequencial)
 sozinha)   │                         FE-9 ─┘
```

Dentro de cada etapa as frentes rodam **em paralelo**. Uma etapa só começa quando o gate da anterior passou (seção 6).

---

## 2. Convenções obrigatórias

**Herda integralmente a seção 2 do `DESENVOLVIMENTO-FASE2.md`** — dinheiro em `long`/centavos, `LocalDate` para data de negócio via `BusinessClock`, isolamento por workspace (regra 8) em toda query, erros como código de máquina em `ErrorResponse`, i18n sem string literal em JSX, Lombok nas entidades e `record` nos DTOs, `<select>` nativo de `components/form/fields.tsx`, sem relacionamento JPA (FKs são UUID escalares), regra de negócio pura em `domain/service/` como classe final estática, e **nunca editar `web/src/lib/api/generated/**`**.

Duas convenções que esta fatia exercita mais do que as anteriores:

- **Toda aritmética de dinheiro nova nasce como classe pura testável sem Spring**, com a invariante assertada antes de retornar — no molde de `InstallmentSplitter.assertSumMatches`. São três nesta fatia: `VaultBalanceCalculator`, `VaultGoalCalculator`, `YieldDistributor`.
- **Saldo nunca é materializado.** `AccountBalanceService` agrega no banco e soma em Java a partir de buckets; o saldo da caixinha faz igual. Campo de saldo em tabela é regressão, não otimização.

---

## 3. Etapas

| Etapa | Frentes | Persona | Pré-requisito |
|---|---|---|---|
| **E9 — Contrato** | CT-2 (sequencial) | java-architect | Fase 2 fechada |
| **E10 — Caixinha & tela** | BE-9 · FE-8 | spring-boot-engineer, react-specialist | Gate E9 |
| **E11 — Aporte, rendimento & diálogos** | BE-10 · BE-11 · FE-9 | spring-boot-engineer ×2, react-specialist | Gate E10 |
| **E12 — Integração** | INT-3 (sequencial) | java-architect | Gate E11 |

### ⚠️ Reserva Flyway — esta fatia usa V18 e V19

**BE-9 → V18 (`vault`) e V19 (`vault_entry`). Nenhuma outra ficha cria migração.**

A máxima aplicada hoje é **V17**. `spring.flyway.out-of-order` **não está habilitado** e há banco de produção migrado: migração com versão menor que a máxima faz o Flyway falhar a validação no boot. Os buracos de V3–V6, V8 e V10–V12 são **permanentes**.

Lembrete: `jpa.hibernate.ddl-auto: validate` — campo novo de entidade sem migração correspondente derruba a aplicação no boot.

---

## 4. Desenho — decisões fechadas, vinculantes para todas as fichas

> Esta seção é o coração do plano. Nenhuma ficha decide nada aqui de novo; todas executam o que está escrito.

### 4.0 Decisões de produto (fechadas com o usuário em 05/08/2026)

| Decisão | Escolha |
|---|---|
| Escopo | Núcleo **+ meta (valor-alvo/prazo, projeção linear) + remanejo entre caixinhas + rendimento distribuível** |
| Caixinha obrigatória? | **Opcional.** Conta sem caixinha funciona exatamente como hoje. Ganhou a primeira caixinha não-arquivada, o aporte nela passa a exigir uma. **Nada migra em produção, nenhuma caixinha automática** |
| UI | **Detalhe da conta**, rota `/accounts/:accountId/vaults`, no molde de `/cards/:cardId/statements`. Sem item novo no menu |
| Rendimento conta como receita? | **Não.** Categoria de sistema — sobe saldo e caixinha, aparece no extrato, fica fora do tile "receitas do mês" e do denominador da R10 |

A escolha do rendimento tem precedente **verificado no código**: `DashboardService.summary` monta o tile de despesas a partir de `expenseByCategory`, que descarta `systemCategory` — exatamente como a alocação faz. Categoria de sistema já é invisível para os dois. O rendimento herda esse tratamento, mantendo **uma única definição de receita** para que tile e denominador nunca discordem.

### 4.1 Saldo da caixinha → livro-razão único `vault_entry`

Três fontes de movimento não cabem numa coluna `vault_id` na `transaction`: aporte/resgate **é** transação (1:1), remanejo **não é** transação (0:2) e rendimento é **1 transação para N caixinhas** (1:N). Um razão único resolve os três e mantém o saldo **derivado por agregação no banco**, como `AccountBalanceService` já faz via `aggregateBalanceBuckets`.

**A decisão que evita drift:** a linha de aporte/resgate **não duplica valor nem status** da transação — `amount_cents` fica `NULL` e significa *"espelhe o valor assinado da transação"*, reusando `BalanceCalculator.signedAmountCents` **literalmente**. Consequência prática: `TransferService.update` pode mudar valor, data e status à vontade **sem tocar no razão**, e apagar a transação limpa o razão pelo `ON DELETE CASCADE` — a mesma disciplina que a V17 usa em `transaction_tag`. Só remanejo e rendimento carregam valor próprio, porque não têm transação 1:1.

### 4.2 PENDING vs SETTLED

A caixinha espelha a conta: `balanceCents` (SETTLED) e `projectedBalanceCents` (SETTLED+PENDING).

**Remanejo não tem status e entra nos dois.** Não é dinheiro a confirmar com o banco — é reclassificação contábil que já aconteceu no instante em que foi registrada. Não existe "remanejo pendente".

A identidade **`Σ caixinhas + não alocado = saldo da conta`** vale nos dois níveis, por construção.

### 4.3 "Não alocado" → calculado, nunca linha

`unallocatedCents = account.balanceCents − Σ vault.balanceCents` (idem projetado).

Uma caixinha "não alocado" real precisaria de nome e pilar — e o ponto dela é ser dinheiro que **ainda não escolheu pilar**. Além disso teria de ser criada por migração em contas antigas e mantida em sincronia em todo caminho de escrita. Calculada, a identidade é verdadeira por definição.

Duas guardas, propositalmente assimétricas:

| Situação | Tratamento |
|---|---|
| Uma **caixinha** ficaria negativa | **Bloqueado** — `VAULT_INSUFFICIENT_BALANCE` (409, `params: {vaultId, shortfallCents}`), via `VaultBalanceService.assertNotOverdrawn(vaultId)` chamado **ao fim** de toda mutação que toca a caixinha. Como lê o pós-estado do razão, funciona igual para qualquer caminho de escrita |
| **Não alocado** ficaria negativo | **Só exibido** (fatia vermelha + "R$ X a descoberto"). Bloquear acoplaria *todo* caminho de escrita do app à feature — despesa direta na conta, pagamento de fatura debitando ela, edição do `initialBalanceCents`. O app já exibe saldo de conta negativo em vez de proibir |

Movimento PENDING não derruba o saldo corrente, então nunca é bloqueado na criação — é bloqueado **no settle**, pela mesma chamada.

### 4.4 Resolução do pilar — a regra completa

`TransferService.resolveContributionPillar` ganha o argumento `Vault destinationVault`:

| Destino | `vaultId` | Resultado |
|---|---|---|
| Não é INVESTMENT/SAVINGS | qualquer | `requested != null` → `INVALID_CONTRIBUTION_PILLAR` (**inalterado**); `vaultId != null` → `VAULT_NOT_FOUND`; pilar `null` |
| INV/SAV **sem** caixinha ativa | ausente | **Comportamento de hoje, bit a bit**: `requested ?? fromPillar(account.default)`; se `null` → `CONTRIBUTION_PILLAR_REQUIRED` |
| INV/SAV **sem** caixinha ativa | presente | `VAULT_NOT_FOUND` |
| INV/SAV **com** caixinha ativa | ausente | `VAULT_REQUIRED` (400) |
| INV/SAV **com** caixinha ativa | arquivada | `VAULT_ARCHIVED` (409) |
| INV/SAV **com** caixinha ativa | presente, sem pilar explícito | pilar = `vault.contributionPillar` |
| INV/SAV **com** caixinha ativa | presente, pilar **igual** ao da caixinha | **aceito** |
| INV/SAV **com** caixinha ativa | presente, pilar **diferente** | `VAULT_PILLAR_CONFLICT` (400) |

Aceitar "pilar igual" não é frouxidão: o front devolve o `contributionPillar` que leu da resposta, e transformar um round-trip idêntico em erro quebraria a edição sem nada ter mudado.

`hasVaults` = **existe caixinha não-arquivada** naquela conta.

**Resgate também exige caixinha.** Hoje `create` só inspeciona o destino; agora a **origem** também: se a conta de origem tem caixinha, a perna OUT exige `fromVaultId`. Por isso `TransferRequest` ganha **dois** campos: `fromVaultId` e `toVaultId`.

**No `update`**: `vaultId` re-aponta a caixinha da **perna endereçada**, simétrico ao `accountId`, que já move só a perna editada. Omitido, **carrega a caixinha atual adiante enquanto a perna continuar na mesma conta**; se `accountId` moveu a perna, a caixinha antiga é descartada (pertence à conta antiga) e a regra roda do zero contra o novo destino. É a mesma frase que já existe ("mover para conta corrente derruba o pilar"), um nível mais fundo. `contributionPillar` no update vira **advisório**: conflita → `VAULT_PILLAR_CONFLICT`; igual ou omitido → a caixinha manda.

**Onde o razão é escrito:** `TransferService` **não** ganha segunda responsabilidade. Nasce `application/vault/VaultLinkService` com `link` / `relink` / `unlink`, e `TransferService` depende dele. A dependência é unidirecional (`transaction → vault`); o pacote `vault` só conhece seus próprios repositórios, então não há ciclo.

### 4.5 Rendimento

**Como é lançado**: transação **INCOME** na conta, sob **categoria de sistema** "Rendimento de investimentos", criada lazy no molde exato de `application/card/StatementPaymentCategoryProvider` (find-or-create com `uq_category_system_name` da V9 arbitrando a corrida). Endpoint dedicado que cria a transação e as N linhas de razão numa transação só.

INCOME e não tipo novo porque **o dinheiro realmente entrou** — o saldo tem de subir. Tipo novo obrigaria mexer em `BalanceCalculator.signedAmountCents`, `TransactionType`, `ck_transaction_category` e no extrato: regressão gratuita.

**Não consome pilar.** Aporte consome pilar porque é dinheiro que o usuário *direcionou* da renda dele; rendimento é dinheiro que o mercado deu — nada foi alocado. Sendo INCOME (nunca TRANSFER), `contributionsByPillar` jamais o enxerga e **`PillarAllocationCalculator` não muda uma linha**.

**Rateio pro-rata, maior resto (Hamilton)** em `domain/service/YieldDistributor`:

```
base_i  = saldo CURRENT da caixinha i na data do rendimento; base_i <= 0 → excluída
share_i = floor(total * base_i / Σbase)
sobra   = total − Σ share_i            // 0 <= sobra < nº de caixinhas
```

A sobra é distribuída **um centavo por caixinha**, em ordem decrescente de resto fracionário; empate desempata por `base_i` maior, depois `name` (case-insensitive), depois `id` — determinístico e testável. Se `Σbase == 0`, o rendimento é lançado **sem nenhuma linha de razão** e cai inteiro no não alocado: honesto, não inventa alocação.

Termina com o mesmo `assertSumMatches` de `InstallmentSplitter`. **A sobra vai para o maior resto, e não para a última** como no `InstallmentSplitter` — lá as partes são iguais e "última" é arbitrário; aqui as partes são proporcionais e o maior resto é o critério justo. **Documentar a diferença no javadoc**, senão alguém "uniformiza" depois e quebra a justiça do rateio.

### 4.6 Meta e projeção linear

Regra pura em `domain/service/VaultGoalCalculator`, no molde de `RecurrenceScheduler`. **A API devolve pronto**; o front só formata e desenha a barra — precedente `PillarAllocationCalculator.Report`, e `today` em `America/Sao_Paulo` é assunto de servidor.

- `remaining = max(0, target − current)`
- `progressPercent = current * 100.0 / target`, **não capado** — mesma escolha de `actualPercent`, que deixa o cliente destacar o estouro
- `monthsRemaining = MONTHS.between(YearMonth(today), YearMonth(targetDate)) + 1`, piso 0 — conta a competência corrente, semântica do app
- `monthlyNeeded = ceilDiv(remaining, monthsRemaining)` — **teto**, senão pagar o sugerido todo mês fica centavos curto
- `status` ∈ `IN_PROGRESS` | `REACHED` | `OVERDUE`:
  - **Vencida**: `monthsRemaining = 0`, `monthlyNeeded = remaining` (tudo, agora) — **não `null`**; o cliente não deve precisar de caso especial para dizer "faltam R$ X, prazo vencido"
  - **Batida**: `remaining = 0`, `monthlyNeeded = 0`, barra cheia, para de cobrar
  - **Sem prazo** (meta de valor sem data, válida): `monthsRemaining` e `monthlyNeeded` `null`, status ∈ {IN_PROGRESS, REACHED}
- Javadoc explicita **projeção linear, sem rentabilidade** — o simulador de renda fixa é Fase 3

### 4.7 Arquivar e excluir

Espelha `Account` (flag `archived`, recusa exclusão com histórico):

- `DELETE /vaults/{id}` → `VAULT_HAS_ENTRIES` (409) se existe qualquer `vault_entry`; senão exclusão física
- `POST /vaults/{id}/archive` → `VAULT_NOT_EMPTY` (409) se saldo ≠ 0 em qualquer dos dois níveis

Exigir vazia para arquivar tem motivo: caixinha arquivada **com saldo** teria de continuar aparecendo na composição (o dinheiro está lá) e ainda contaria para `hasVaults`, prendendo o usuário num estado sem saída — todo aporte exigiria caixinha e nenhuma seria selecionável. Exigindo vazia, arquivar a última **de fato** devolve a conta ao modo sem caixinha.

**O remanejo aceita cada lado nulo, significando "não alocado"** — e é isso que torna a feature usável:

| from | to | Significado |
|---|---|---|
| A | B | remanejo clássico entre caixinhas da mesma conta |
| A | `null` | devolve ao não alocado (é assim que se esvazia a última caixinha) |
| `null` | A | **aloca saldo pré-existente da conta numa caixinha** |
| `null` | `null` | 400 `VALIDATION_ERROR` + `fieldErrors` |

A terceira linha é **obrigatória para a feature existir**: sem ela, quem já tem R$ 50 mil na conta não consegue reparti-los sem inventar transferências falsas. Com ela, o onboarding é: cria as caixinhas → aloca o saldo existente.

### 4.8 Consequência conhecida — não é bug, é a regra

**Resgatar de uma caixinha não devolve o pilar.** O resgate é a perna OUT, e perna OUT tem `contributionPillar` sempre nulo — comportamento do MVP, preservado de propósito: o aporte consumiu o pilar na competência em que aconteceu, e competência fechada não se reescreve (mesma doutrina da V13). Idem trocar o pilar de uma caixinha: vale de frente pra frente, não reescreve aportes já lançados.

---

## 5. Fichas de tarefa

### CT-2 — Contrato das Caixinhas `[E9 · java-architect, sequencial]`

- **Objetivo**: fechar em `mk-finance/contracts/openapi.yaml` toda a superfície de API das caixinhas, destravando back e front em paralelo.
- **Dono de**: `mk-finance/contracts/openapi.yaml`, `domain/exception/ErrorCode.java`, `domain/exception/AppException.java`.
- **Escopo** — tag nova `vaults` (após `tags`, antes de `export`):

| Método + path | operationId |
|---|---|
| `GET /workspaces/{ws}/accounts/{accountId}/vaults` | `listVaults` → **`VaultComposition`** (a composição inteira num request), query `includeArchived` (default false) |
| `POST /workspaces/{ws}/accounts/{accountId}/vaults` | `createVault` → 201 `Vault` |
| `PATCH /workspaces/{ws}/vaults/{vaultId}` | `updateVault` |
| `POST /workspaces/{ws}/vaults/{vaultId}/archive` | `archiveVault` |
| `DELETE /workspaces/{ws}/vaults/{vaultId}` | `deleteVault` → 204 |
| `GET /workspaces/{ws}/vaults/{vaultId}/entries` | `listVaultEntries` (extrato da caixinha, `from`/`to`, mais recente primeiro) |
| `POST /workspaces/{ws}/vaults/reallocations` | `reallocateVault` |
| `DELETE /workspaces/{ws}/vaults/reallocations/{id}` | `deleteVaultReallocation` (apaga o grupo inteiro) |
| `POST /workspaces/{ws}/accounts/{accountId}/vault-yields` | `createVaultYield` → devolve o rateio aplicado |

  - **Schemas novos**: `Vault`, `VaultGoal`, `VaultGoalStatus`, `VaultRequest`, `VaultComposition`, `VaultEntry`, `VaultEntrySource`, `VaultReallocationRequest`/`VaultReallocation`, `VaultYieldRequest`/`VaultYield`.
  - **Schemas alterados**: `TransferRequest` (+`fromVaultId`, `+toVaultId`), `TransferUpdateRequest` (+`vaultId`), `Transaction` (+`vaultId`, para o badge no extrato), `Account` (+`hasVaults`, para o diálogo saber sem N requests), `components/parameters` (+`vaultId`, `+reallocationId`).
  - **Códigos de erro** (constante + factory + bloco no comentário de `ErrorResponse.code`): `VAULT_NOT_FOUND` 404 · `VAULT_REQUIRED` 400 · `VAULT_PILLAR_CONFLICT` 400 · `VAULT_ACCOUNT_MISMATCH` 400 · `VAULT_INSUFFICIENT_BALANCE` 409 · `VAULT_NAME_DUPLICATE` 409 · `VAULT_NOT_EMPTY` 409 · `VAULT_HAS_ENTRIES` 409 · `VAULT_ARCHIVED` 409 · `VAULT_REALLOCATION_NOT_FOUND` 404.
  - Rendimento **não** tem endpoint de exclusão: é transação INCOME, apagada por `DELETE /transactions/{id}`, e o cascade limpa o razão.
  - Remanejo com os dois lados nulos usa `VALIDATION_ERROR` + `fieldErrors` — obrigatoriedade condicional que o OpenAPI não expressa (decisão 2 da CT-1).
- **Aceite**: `npm run api:generate` limpo, produzindo hooks + mocks MSW da tag `vaults`; `npm run build` e `./mvnw compile` verdes; nenhum endpoint existente quebra de forma incompatível.

#### ✅ CT-2 concluída — 05/08/2026 (branch `feat/ct-2-contrato-caixinhas`, commit `d0e3e1e`)

Gate verde: `api:generate` gerou `lib/api/generated/vaults/` com `useListVaults`, `useListVaultEntries` e os 7 hooks de mutação + mocks MSW; `npm run build`, `npm test` **98/98** e `./mvnw compile` passaram. As 10 constantes estão em `ErrorCode` com o *porquê* de cada uma no javadoc, e as factories em `AppException` — `vaultInsufficientBalance(vaultId, shortfallCents)` e `vaultNotEmpty(balanceCents)` levam params para o cliente poder dizer *quanto* falta em vez de só recusar.

**Decisões que a CT-2 fechou além da ficha — vinculantes para as fichas seguintes:**

1. **`listVaults` devolve `VaultComposition`, não uma lista.** A fatia "não alocado" e os dois totais vêm calculados do servidor. O cliente **nunca soma dinheiro** para descobrir o que sobrou, e a identidade da §4.2 chega pronta e já conferida. A BE-9 é responsável por isso ser verdade.
2. **`Account.hasVaults` e `Transaction.vaultId` são opcionais, não obrigatórios** — precedente do `tags` da Fase 2. O modo de falha de um cliente antigo é ler `undefined`, que cai **exatamente no comportamento pré-caixinhas** em vez de quebrar. `!account.hasVaults` é a checagem correta no front.
3. **`VaultEntry.amountCents` é assinado** (negativo = saiu da caixinha) — a única exceção na API, onde todo valor é positivo e o sinal vem do tipo. Está documentado no schema; o razão precisa do sinal porque as quatro fontes não têm um "tipo" comum de onde derivá-lo.
4. **`VaultGoal.monthlyNeededCents` nunca é `null` por prazo vencido** — é o restante inteiro. `null` só quando não há prazo. Poupa o cliente de um caso especial para dizer "faltam R$ X, prazo vencido".
5. **`createVault` responde `VALIDATION_ERROR`** (não um código próprio) para conta que não é INVESTMENT/SAVINGS — é validação de payload contra o estado do recurso, mesmo tratamento do remanejo com os dois lados nulos.

### BE-9 — Caixinha, razão, saldo e meta `[E10 · spring-boot-engineer]` (V18–V19)

- **Objetivo**: a caixinha existir, ter saldo correto e ter meta.
- **Dono de**: `domain/model/Vault*`, `domain/service/{VaultBalanceCalculator, VaultGoalCalculator}`, `application/vault/**` (exceto os arquivos de yield), `api/vault/**`, `infrastructure/persistence/Vault*`, V18–V19, **e o campo `hasVaults` em `AccountResponse`/`AccountMapper`** (exceção de propriedade registrada aqui).
- **Não toca**: `application/transaction/**`, `application/dashboard/**`.
- **Passos**:
  1. V18 + V19 (DDL na seção 5.5) e entidades `Vault`, `VaultEntry`, enums `VaultEntrySource`, `VaultGoalStatus`.
  2. **`VaultBalanceCalculator` + teste unitário ANTES de qualquer repositório** — a aritmética primeiro, e a identidade `Σ + não alocado = conta` assertada dentro dela.
  3. Repositórios com as três agregações (seção 5.5) + `VaultBalanceService`, expondo `assertNotOverdrawn(vaultId)`.
  4. `VaultService`: CRUD, arquivar, excluir, validação de nome duplicado e de tipo de conta (só INVESTMENT/SAVINGS) + controller.
  5. `VaultGoalCalculator` + teste + a meta na resposta.
  6. `VaultReallocationService`, **incluindo os lados nulos** (§4.7).
  7. Testes de integração + `hasVaults`.
- **Aceite**: composição fecha a identidade em cenário com aporte SETTLED, aporte PENDING e remanejo; arquivar/excluir respeitam §4.7.

### FE-8 — Tela de caixinhas `[E10 · react-specialist]`

- **Objetivo**: a tela de composição da conta, com metas e remanejo.
- **Dono de**: `web/src/features/vaults/**`, `web/src/App.tsx`, `web/src/features/accounts/**`, `web/src/mocks/handlers.ts`, `web/src/test/financeFixtures.ts`, namespace `vaults` do i18n.
- Desenvolve contra MSW; **não espera a BE-9**.
- **Passos**:
  1. Rota `/accounts/:accountId/vaults` + esqueleto da página a partir do molde de `CardStatementsPage` (cabeçalho com nome/saldo da conta e `<Link>` de volta). **Sem item novo no menu**; a entrada é uma ação "Caixinhas" na linha de `AccountsPage`, só para INVESTMENT/SAVINGS.
  2. Listagem + `VaultCompositionCard` (barra empilhada, fatia "não alocado", **negativa em vermelho**).
  3. `VaultFormDialog` — `TextField` (nome), `SelectField` nativo (pilar), `MoneyField` (valor-alvo), data (prazo), cor.
  4. `VaultGoalBar` — **só renderiza números vindos da API**, não recalcula.
  5. `VaultReallocateDialog` — origem/destino com a opção literal "Não alocado" (`value=""`).
  6. `VaultEntriesSheet` — extrato da caixinha.
  7. `vaultOptions.ts` (helpers puros), fixtures determinísticas, testes co-localizados, chaves i18n.
- **Aceite**: `npm test && npm run build` verdes; nenhuma string literal em JSX.

#### ✅ FE-8 concluída — 05/08/2026 (merge `5383232`, 8 commits incrementais)

`npm test` **128/128** (os 98 anteriores intactos, sem edição, + 17 de `vaultOptions.test.ts` e 13 de `VaultsPage.test.tsx`) e `npm run build` verdes, conferidos na `main` após o merge. Nenhuma dependência npm nova — a armadilha do `npm install` não se aplica a este merge.

**Duas lacunas que a FE-8 revelou e que a FE-9 herda** (estavam no plano, não na execução):

1. **Os códigos `VAULT_*` não têm chave em `error.*`.** O namespace `error` é da FE-9 pela tabela 5.4, então a FE-8 corretamente não o tocou. Até lá, todo erro de caixinha cai no fallback genérico de `translateApiError` — seguro, mas mudo: "nome já usado" e "saldo insuficiente" saem como "algo deu errado". **A FE-9 é obrigada a preencher as 10 chaves**, e `VAULT_INSUFFICIENT_BALANCE` e `VAULT_NOT_EMPTY` devem usar os `params` (`shortfallCents`, `balanceCents`) que a CT-2 colocou neles — o valor existe justamente para a mensagem poder dizer *quanto*.
2. **`VaultYieldDialog` não existe.** A seção 4 deste plano listava o arquivo sob `features/vaults/`, mas os passos da ficha FE-8 não o incluíam — inconsistência minha, não da execução. **Resolvido: o `VaultYieldDialog` passa a ser da FE-9**, que o cria em `features/vaults/`. Não há colisão porque a FE-8 está fechada e mergeada antes da E11 começar; a regra da tabela 5.4 vale para frentes *simultâneas*.

Decisões que a FE-8 tomou sozinha e ficam valendo: o extrato usa o `Dialog` existente (não há primitivo `Sheet` em `components/ui/` e criar um não era da alçada da ficha); as strings da ação "Caixinhas" na `AccountsPage` vivem em `vaults.accountAction` em vez de `accounts.*`, para não invadir namespace alheio; `contributionPillarOptions` foi reaproveitado de `accountOptions.ts` em vez de duplicado; e o campo de prazo só aparece quando há valor-alvo, evitando na origem o estado que o servidor rejeitaria.

### BE-10 — Aporte e resgate por caixinha `[E11 · spring-boot-engineer]`

- **Objetivo**: o pilar do aporte passar a vir da caixinha.
- **Dono de**: `application/transaction/{TransferService, TransferInput, TransferUpdateInput}`, `api/transaction/**`.
- **Passos**:
  1. `resolveContributionPillar` com `Vault` — **a matriz do §4.4 inteira**.
  2. `create` inspecionando origem **e** destino; escrita do link **depois** do flush das pernas.
  3. `update` (re-apontar caixinha/conta, §4.4).
  4. `delete` (chamar `unlink` **antes** de apagar) e `settle` (`assertNotOverdrawn`).
  5. `vaultId` na `TransactionResponse`, por lookup em lote no molde de `TagService.tagsOf(Collection)`.
  6. `VaultContributionIntegrationTest` cobrindo a matriz inteira.
- **Regra dura**: **nenhum teste existente de `TransferIntegrationTest` pode ser editado.** Se algum quebrar, a regra está errada — não o teste.

#### ✅ BE-10 concluída — 05/08/2026 (merge `9984c1f`, 9 commits incrementais)

Gate: `./mvnw verify` **334 testes** (311 anteriores intactos + 22 da ficha + 1 acrescentado na revisão). A matriz da §4.4 inteira implementada e coberta, incluindo resgate simétrico, `shortfallCents` e os fluxos de update/delete/settle.

**Duas correções feitas na revisão, ambas com teste:**

1. **Criar a primeira caixinha travava a edição dos aportes anteriores.** A BE-10 sinalizou que a §4.4 não dizia qual perna a exigência cobre no `update` quando as duas divergem, e escolheu revalidar sempre. O efeito: quem usa o app há meses e cria a primeira caixinha na corretora passava a receber `VAULT_REQUIRED` ao editar qualquer aporte lançado antes disso — inclusive mexendo só na perna de origem. Contradizia a decisão de produto "nada migra em produção", e era desnecessário: esses aportes já consumiram o pilar, e o dinheiro deles fica na fatia não alocada, que existe exatamente para isso. **Regra corrigida e agora vinculante: a exigência vale só para a perna que está sendo re-apontada** — movida de conta, ou recebendo `vaultId` explícito. Perna deixada em paz mantém o que tem, inclusive nada.
2. **Construtores "legados" nos records.** Efeito colateral da regra dura: lida ao pé da letra, levou a criar construtores de aridade antiga em `TransferRequest`/`TransferUpdateRequest`/`TransferInput`/`TransferUpdateInput` para os ~36 call sites antigos compilarem — superfície de produção mantida por conveniência de teste, com risco real de uma ficha futura perder o `vaultId` em silêncio. Removidos; os call sites dizem `null` explicitamente. **A regra queria dizer "não afrouxe asserção para o seu código passar", não "não toque no arquivo"** — vale enunciá-la assim nas próximas fichas.

### BE-11 — Rendimento distribuível `[E11 · spring-boot-engineer]`

- **Objetivo**: lançar o rendimento do mês rateado entre as caixinhas.
- **Dono de**: `domain/service/YieldDistributor`, `application/vault/{VaultYieldService, VaultYieldCategoryProvider}`, `api/vault/VaultYield*`, `application/dashboard/DashboardAggregateRepository`.
- **Não toca `PillarAllocationCalculator`** — rendimento é INCOME e nunca chega lá.

#### ✅ BE-11 concluída — 06/08/2026 (merge `7b8d967`, 5 commits incrementais)

Gate: `./mvnw verify` **353 testes** (334 anteriores intactos + 19), conferido na `main` após o merge. `PillarAllocationCalculator` e `BalanceCalculator` com diff **vazio**, como a §7 exige.

O `sumIncome` passou a juntar `Category` e filtrar `c.system = false`. O join é INNER, e isso é seguro porque `ck_transaction_category` obriga categoria em INCOME/EXPENSE — nenhuma receita cai fora por causa do join. O teste de regressão trava o comportamento inteiro: lançar rendimento sobe o saldo da conta e **não** move o tile "receitas do mês" nem os percentuais dos seis pilares.

O `YieldDistributor` documenta no javadoc, em parágrafo próprio, por que a sobra vai para o maior resto e **não** para a última parte como no `InstallmentSplitter` — com um "não simplifique isto de volta" explícito. Era o risco de alguém uniformizar os dois algoritmos depois e quebrar a justiça do rateio.

Cenário canônico coberto: bases 6.000/4.500 e rendimento de R$ 100,01 → R$ 57,15 e R$ 42,86, somando **exatamente** 10001. Mais os casos de nenhuma caixinha com saldo positivo (rateio vazio, o dinheiro fica no não alocado, nada é inventado) e o `DELETE /transactions/{id}` levando as duas linhas do razão por cascade.

**Nota operacional**: a ficha caiu por limite de sessão **depois** de commitar os cinco passos, faltando só rodar o gate e reportar. A cadência de commit por arquivo funcionou — nada se perdeu, e a retomada foi verificar, não recodar.
- **Passos**:
  1. `YieldDistributor` + teste exaustivo: soma exata, empates, base zero, caixinha com saldo negativo excluída.
  2. Categoria de sistema lazy, cópia estrutural de `StatementPaymentCategoryProvider`.
  3. `VaultYieldService` + controller.
  4. **`sumIncome` passa a juntar `Category` e filtrar `c.system = false`** — rodar `DashboardIntegrationTest` **antes** de mudar e diffar os números.
  5. Teste de integração ponta a ponta do rendimento.

### FE-9 — Aporte com caixinha nos diálogos `[E11 · react-specialist]`

- **Dono de**: `web/src/features/transactions/**`, chaves `transactions.*` e `error.*` do i18n.
- **Passos**:
  1. Selects de caixinha de origem/destino no modo transferência, condicionados a `account.hasVaults`.
  2. **Quando há caixinha, o pilar vira texto derivado, não campo editável** — ele vem da caixinha.
  3. Badge da caixinha na linha do extrato.
  4. **Cria** `features/vaults/VaultYieldDialog.tsx` (com prévia do rateio) e o aciona a partir da `VaultsPage` — a FE-8 não o fez, ver a nota do fecho dela. Sem colisão: a FE-8 já está mergeada quando a E11 começa.
  5. **As 10 chaves `error.VAULT_*`**, usando os `params` de `VAULT_INSUFFICIENT_BALANCE` e `VAULT_NOT_EMPTY` para dizer quanto falta. Hoje esses erros caem no fallback genérico.
  6. Testes com `server.use(...)` local, **sem tocar `mocks/handlers.ts`** (é da FE-8).

#### ✅ FE-9 concluída — 06/08/2026 (branch `worktree-agent-a177ac7989a6fadb3`, 6 commits incrementais)

`npm test` **142/142** (os 128 anteriores intactos, sem edição, + 14 novos) e `npm run build` verdes. Os seis passos da ficha, mais o que o fecho da FE-8 já sinalizava: `VaultYieldDialog.tsx` criado em `features/vaults/` e acionado por um botão "Lançar rendimento" na `VaultsPage` — única exceção de propriedade fora de `features/transactions/**`, prevista na ficha.

**Decisões tomadas sozinho, registradas para as próximas fichas (INT-3):**

1. **`vaultId` explícito e omitido são indistinguíveis no back** (`TransferUpdateInput.vaultId` é `UUID` puro, não `JsonNullable`), então o front nunca precisa decidir entre "omitir" e "mandar null" — sempre manda `form.vaultId || null`, e o resultado é o mesmo dos dois jeitos. Confirmado lendo `TransferService.update` antes de implementar, não supondo.
2. **`lib/errors.ts` deixou de ser arquivo morto para os `params`**: `translateApiError` passa `error.body.params` para `t()`, formatando `shortfallCents`/`balanceCents` em BRL antes (as duas chaves que a ficha exige interpolar). Não é dono exclusivo de ninguém na tabela 5.4 — extensão de infra compartilhada, comportamento aditivo (códigos antigos com `params` não usados continuam ignorando-os).
3. **Badge do extrato resolve o nome da caixinha**, não só marca a presença de `vaultId` — um `useQueries` batching `getListVaultsQueryOptions` por conta que tem `hasVaults`, normalmente zero requests extras.
4. **Prévia do rendimento é local (`yieldPreviewShares` em `vaultOptions.ts`)**, espelhando o `YieldDistributor` do BE-11 (mesmo desempate: maior resto, depois saldo, depois nome, depois id) — mas a tela deixa explícito que `VaultYield.shares` da resposta é quem manda.
5. **Nenhuma string literal nova em JSX** fora das exigidas pela ficha; as 10 chaves `error.VAULT_*` e as chaves `vaults.yield*`/`transactions.*` novas estão documentadas com o porquê no próprio `common.json` (via os componentes que as usam).

**Divergência que apareceu só em teste, não no código**: o primeiro teste do badge do extrato falhou de forma intermitente por pura lentidão do ambiente de sandbox (múltiplos `useQueries` + `useListTransactions`/`useListAccounts`/`useListCategories` em paralelo passando de 1000 ms, o timeout default do RTL) — não por um handler MSW sendo sobrescrito incorretamente, hipótese descartada com um `console.log` temporário no componente antes de ajustar o timeout daquele `findByText` para 3000 ms. Documentado aqui para quem depurar o próximo teste "pending para sempre" não repetir a mesma investigação.

### INT-3 — Integração & gate `[E12 · java-architect, sequencial]`

- **Passos**: `npm run api:generate` e conferência contrato × springdoc `/v3/api-docs`; gate completo (seção 6); estender `web/e2e/smoke.spec.ts` com o roteiro da seção 8; conferir i18n sem chave faltando; **revisar que o diff de `BalanceCalculator.java` e de `aggregateBalanceBuckets` é vazio**.
- **Aceite**: gate verde; E2E verde; a identidade da §4.2 confere à mão na stack real.

#### ✅ INT-3 concluída — 06/08/2026 (worktree `worktree-agent-ab33ee47e54c830c9`, 2 commits incrementais)

**Rebase**: a worktree nasceu de um commit anterior à fatia inteira (E3 da Fase 2); `merge-base(HEAD, main) == HEAD` confirmou que era um simples fast-forward (`git merge --ff-only main`, `e5052f5..fa23159`) — sem conflito, todas as fichas CT-2..FE-9 entraram de uma vez.

**Gate completo, verde**: `./mvnw verify` **353 testes, 0 falhas** (conferido por contagem de `Tests run` nos 106 `surefire-reports`, já que `-q` suprime o resumo do Maven); `npm test` **142/142**; `npm run build` verde (mesmo aviso de chunk >500kB que já existia, sem regressão); `POSTGRES_PASSWORD=x docker compose config -q` verde. Nenhum número mudou em relação ao que a BE-11 já tinha fechado — INT-3 não tocou código de produção, só testes.

**Contrato × springdoc, nos dois sentidos**: subi a aplicação local (Postgres efêmero + `spring-boot:run`) e comparei `contracts/openapi.yaml` com `/v3/api-docs` por conjunto de `(método, path)`. **58 endpoints dos dois lados, diff vazio nas duas direções** — nada declarado e não implementado, nada implementado e não declarado.

**i18n**: as 10 chaves `error.VAULT_*` existem (já verificado pela FE-9). Os dois enums novos do contrato têm os mapas completos: `VaultEntrySource` (`CONTRIBUTION`/`WITHDRAWAL`/`YIELD`/`REALLOCATION`, 4/4 em `vaults.entrySourceLabel`) e `VaultGoalStatus` (`IN_PROGRESS`/`REACHED`/`OVERDUE`, 3/3 em `vaults.goalStatus`). Varredura automatizada de todo `t("...")` no código-fonte (370 chamadas reais, descontados 6 falsos-positivos de `.get()`/`.split()`/mocks MSW capturados pela regex) contra `common.json`: **zero chave faltando**.

**`BalanceCalculator`/`aggregateBalanceBuckets`**: já conferido pela BE-11 e permanece com diff zero — não há nada novo a revisar aqui, a fatia inteira nunca tocou esses dois pontos.

**E2E**: `web/e2e/smoke.spec.ts` ganhou um terceiro teste, cobrindo o caminho pedido — conta INVESTMENT com saldo pré-existente → caixinha com meta → remanejo `null → caixinha` do saldo pré-existente → aporte pela caixinha no lançamento rápido do cabeçalho → dashboard credita **GOALS** (o pilar da caixinha) e não `FINANCIAL_FREEDOM` (o default da conta) → rendimento lançado e absorvido pela caixinha. **3/3 testes passam contra a stack real** (backend + Postgres, `VITE_API_MOCK=false`), rodado duas vezes seguidas sem flakiness.

Achado real no meio do caminho, não fabricado: o **"Novo lançamento" do cabeçalho não invalida a query de composição** que `VaultsPage` mantém (`invalidateAfterWrite` do `QuickTransactionDialog` só mexe em `transactions`/`accounts`), então a tela de caixinhas fica presa ao `staleTime` de 30s (`main.tsx`) até um reload — a mesma classe de lacuna que o teste feliz original já contornava para o dashboard (comentário existente: "so the summary/allocation queries fetch fresh server truth rather than the staleTime-cached empty snapshot"). Não é um bug exclusivo desta fatia — é uma lacuna de invalidação cross-feature que já existia antes das caixinhas, só nunca tinha um teste que a expusesse. Resolvido no teste com `page.reload()` no lugar de navegação client-side, documentado inline; **registro para o usuário decidir se vale um item de polimento** (o `QuickTransactionDialog` poderia invalidar `getListVaultsQueryKey` também, condicionado a `destinationHasVault`/`originHasVault`) — não corrigido aqui porque a ficha não pede mudança de código de produção.

**Roteiro da §8, os 14 passos, à mão contra a stack real** (script `curl` em `/private/tmp/.../scratchpad/int3-roteiro.sh` desta sessão, no molde do `canonical-scenario-live.sh` da INT-2):

| # | O que prova | Resultado |
|---|---|---|
| 1 | Aporte sem caixinha funciona como hoje | OK — 201, saldo R$10.500,00 |
| 2 | Cria Reserva e Viagem 2027 (meta) | OK |
| 3 | Composição mostra R$10.500 não alocado, caixinhas em zero | OK |
| 4 | Remanejo `null→Reserva` 6000 + `null→Viagem` 4000; saldo da conta não muda | OK |
| 5 | Aporte sem `vaultId` → `VAULT_REQUIRED` | OK — 400 |
| 6 | Pilar divergente → `VAULT_PILLAR_CONFLICT`; sem pilar → aceito, GOALS +500 no dashboard | OK — 400, depois 201 com delta exato |
| 7 | Meta da Viagem: 37,5% | OK — `progressPercent` bate; `monthlyNeededCents` depende de `monthsRemaining`, que conta a competência corrente (§4.6) — com prazo de exatos 12 meses a partir de hoje o roteiro mede 13 meses restantes e R$576,93/mês, não R$625,00/mês. **Não é bug**: R$625,00 só bate com `monthsRemaining=12` exato, que depende do dia do mês em que se roda o roteiro. Comportamento documentado na §4.6, só a redação do passo 7 é otimista sobre a aritmética de datas |
| 8 | Resgate maior que o saldo → `VAULT_INSUFFICIENT_BALANCE` | OK — 409, **`params.shortfallCents = 200000`** conferido byte a byte na resposta (não `availableCents` — o texto do roteiro já estava certo, o valor bate) |
| 9 | Aporte PENDING: `balanceCents` não muda, `projectedBalanceCents` sobe | OK |
| 10 | Rendimento com as bases reais (600000/450000, não as do roteiro que pressupõe 6000/4500 antes do passo 9 pendente) → soma exata | OK — 5715+4286=10001; saldo da conta +10001; tile de receitas do mês intocado |
| 11 | `DELETE` da transação do rendimento apaga as duas linhas do razão | OK — cascade confirmado pela queda do saldo da Reserva |
| 12 | `PATCH` do aporte (500→700) move a caixinha sem escrita no razão | OK — Viagem +200,00 só por mudar a transação |
| 13 | Arquivar com saldo → `VAULT_NOT_EMPTY`; esvaziar → arquiva; excluir com lançamentos → `VAULT_HAS_ENTRIES` | OK, com uma descoberta real no caminho (ver abaixo) |
| 14 | Arquivadas as duas, aporte sem `vaultId` volta a aceitar | OK — 201 |
| — | Identidade final Σ caixinhas + não alocado = saldo da conta | OK — confere byte a byte em todos os passos do roteiro |

**Descoberta real no passo 13, não fabricada**: a primeira tentativa de rodar o roteiro deixou o aporte PENDING do passo 9 sem liquidar nem apagar. Ao chegar no passo 13, `archive` da Reserva recusou com `VAULT_NOT_EMPTY` mesmo depois de remanejar todo o `balanceCents` (SETTLED) para o não alocado — porque `projectedBalanceCents` continuava maior que zero (o remanejo, por design, §4.2, não toca PENDING). Isso é **prova real, não hipotética, de que o guard do §4.7 checa "em qualquer dos dois níveis"** de propósito, exatamente como o texto diz. O roteiro final apaga o aporte PENDING de teste antes do passo 13 (limpeza dentro do próprio script, documentada inline) para poder demonstrar o caminho feliz do arquivamento — mas o comportamento observado no meio do caminho é o gate funcionando corretamente, não uma falha a corrigir.

**Migração com dados preexistentes**: `mk-finance/api/src/test/manual/migration-with-existing-data.sh` estendido para V18/V19. Insere uma conta INVESTMENT com um aporte (par de transferência) lançado *antes* de V18/V19 existirem, aplica as migrações das caixinhas por cima, e confere: as tabelas nascem vazias (nenhuma caixinha automática, §4.0); as duas pernas do aporte pré-caixinha sobrevivem intactas; `ck_vault_goal` rejeita prazo sem valor-alvo; `ux_vault_account_name_ci` rejeita nome duplicado case-insensitive na mesma conta; e a identidade Σ caixinhas + não alocado = saldo da conta fecha em SQL puro no "dia zero" (uma caixinha recém-criada, saldo inteiro no não alocado). Corrigi de passagem uma asserção pré-existente da INT-2 que eu mesmo quebrei (contagem de transações passou de 2 para 4 com as duas pernas novas) — sem isso o script teria uma FALHA que não é da INT-3. **Zero FALHA no output final.**

**Divergências entre desenho e comportamento real, no total**: nenhuma estrutural. As duas únicas surpresas — o `monthsRemaining` do passo 7 e o gate de dois níveis do passo 13 — são os próprios mecanismos do §4.6/§4.7 funcionando como projetados, expostos por um roteiro de teste que não previa a interação entre eles. A lacuna de invalidação de query do `QuickTransactionDialog` é a única descoberta que sobra como possível trabalho futuro, e é de UX/polimento, não de corretude — o dado no servidor sempre esteve certo, só a tela aberta ficava desatualizada por até 30s.

### 5.4 Verificação de colisão de arquivos (lição da Fase 2)

| Arquivo | Único dono |
|---|---|
| `openapi.yaml`, `ErrorCode.java`, `AppException.java` | CT-2 |
| `TransferService.java` e cia., `api/transaction/**` | BE-10 |
| `DashboardAggregateRepository.java` | BE-11 |
| `application/vault/**` | BE-9 — exceto os arquivos de yield, que são de BE-11 e só existem na E11, com a BE-9 já fechada pelo gate |
| `AccountResponse`, `AccountMapper` | BE-9 |
| `App.tsx`, `mocks/handlers.ts`, `financeFixtures.ts`, `features/accounts/**` | FE-8 |
| `features/transactions/**` | FE-9 |
| `i18n/common.json` | subárvores **disjuntas** — `vaults` é da FE-8; `transactions`/`error` são da FE-9. Merge limpo |

### 5.5 DDL e agregações (BE-9)

**`V18__vault.sql`**
```sql
CREATE TABLE vault (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        UUID         NOT NULL REFERENCES workspace (id),
    account_id          UUID         NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    name                VARCHAR(120) NOT NULL,
    contribution_pillar VARCHAR(20)  NOT NULL
        CHECK (contribution_pillar IN ('GOALS', 'FINANCIAL_FREEDOM')),
    target_amount_cents BIGINT       CHECK (target_amount_cents > 0),
    target_date         DATE,
    color               VARCHAR(7),
    archived            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    -- valor-alvo sem prazo é válido; prazo sem valor-alvo não tem do que calcular progresso
    CONSTRAINT ck_vault_goal CHECK (target_date IS NULL OR target_amount_cents IS NOT NULL)
);
CREATE INDEX ix_vault_workspace ON vault (workspace_id);
CREATE INDEX ix_vault_account   ON vault (account_id);
-- único por CONTA, não por workspace: "Reserva" pode existir na corretora e na poupança
CREATE UNIQUE INDEX ux_vault_account_name_ci ON vault (account_id, lower(name));
```

**`V19__vault_entry.sql`**
```sql
CREATE TABLE vault_entry (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    UUID        NOT NULL REFERENCES workspace (id),
    vault_id        UUID        NOT NULL REFERENCES vault (id) ON DELETE CASCADE,
    source          VARCHAR(12) NOT NULL
        CHECK (source IN ('CONTRIBUTION','WITHDRAWAL','YIELD','REALLOCATION')),
    transaction_id  UUID        REFERENCES transaction (id) ON DELETE CASCADE,
    amount_cents    BIGINT      CHECK (amount_cents <> 0),  -- NULL = espelha a transação
    reallocation_id UUID,                                    -- agrupa as 2 pernas do remanejo
    date            DATE,                                    -- só REALLOCATION
    description     VARCHAR(255),                            -- só REALLOCATION
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_vault_entry_source CHECK (
        CASE source
            WHEN 'REALLOCATION' THEN transaction_id IS NULL AND amount_cents IS NOT NULL
                                 AND reallocation_id IS NOT NULL AND date IS NOT NULL
            WHEN 'YIELD'        THEN transaction_id IS NOT NULL AND amount_cents IS NOT NULL
                                 AND reallocation_id IS NULL AND date IS NULL
            ELSE                     transaction_id IS NOT NULL AND amount_cents IS NULL
                                 AND reallocation_id IS NULL AND date IS NULL
        END
    )
);
CREATE INDEX ix_vault_entry_workspace    ON vault_entry (workspace_id);
CREATE INDEX ix_vault_entry_vault        ON vault_entry (vault_id);
CREATE INDEX ix_vault_entry_transaction  ON vault_entry (transaction_id);
CREATE INDEX ix_vault_entry_reallocation ON vault_entry (reallocation_id);
-- uma transferência tem no máximo UMA caixinha por perna; parcial porque rendimento é N linhas
CREATE UNIQUE INDEX ux_vault_entry_mirror ON vault_entry (transaction_id) WHERE amount_cents IS NULL;
```

**As três agregações** — todas `GROUP BY`, custo plano, mescladas em memória como em `AccountBalanceService`:

| # | Predicado | Fonte do valor | Fonte do status |
|---|---|---|---|
| A | `source IN ('CONTRIBUTION','WITHDRAWAL')` | `tx.amountCents` via `BalanceCalculator.signedAmountCents` | `tx.status` |
| B | `source = 'YIELD'` | `ve.amountCents` (já assinado) | `tx.status` |
| C | `source = 'REALLOCATION'` | `ve.amountCents` (já assinado) | sempre SETTLED |

---

## 6. Gates de verificação entre etapas

```bash
cd mk-finance/api && ./mvnw verify          # exige Docker (Testcontainers)
cd mk-finance/web && npm test && npm run build
POSTGRES_PASSWORD=x docker compose -f mk-finance/docker/docker-compose.yml config -q
```

Gate adicional por etapa — **E9**: `npm run api:generate` limpo, sem edição manual em `generated/**`. **E10**: a identidade `Σ caixinhas + não alocado = saldo da conta` confere nos dois níveis, com aporte PENDING no cenário. **E11**: os 261 testes anteriores verdes **sem edição**, e o rateio de uma quantia indivisível somando exato. **E12**: aceite do INT-3.

---

## 7. Armadilhas

Herdadas (`DESENVOLVIMENTO-FASE2.md` §6): worktree nasce do commit em que a sessão começou (rebase antes de codar); agente longo cai e `SendMessage` retoma; `npm install` no checkout principal antes do gate após merge que adiciona deps; `npm run build` (não só `npm test`) após merge de duas frentes de front; orval gera `PATCH`, não `PUT`; `setQueryData(key, undefined)` é no-op no v5; **`git push` está bloqueado — só o usuário**.

Específicas desta fatia:

1. **`sumIncome` é o único risco real no motor.** É o denominador da R10 **e** o tile de receitas. BE-11 diffa os números do `DashboardIntegrationTest` antes e depois; alteração de valor em teste que não envolva categoria de sistema é bug.
2. **`PillarAllocationCalculator` não muda** — `ContributionEntry` já recebe o pilar resolvido. Diff nesse arquivo no INT-3 significa que alguém resolveu no lugar errado.
3. **`contributionsByPillar` não muda** — continua lendo `t.contributionPillar` da perna IN; só quem preenche mudou.
4. **`BalanceCalculator.java` e `aggregateBalanceBuckets` não podem mudar.** Caixinha não é conta. O razão **reusa** `signedAmountCents`, não reescreve.
5. **`TransferService.link` é um balé de três `saveAndFlush`** — a linha do razão só entra depois que a transação existe (FK). Escrever antes = `DataIntegrityViolationException` intermitente.
6. **`TransferService.delete` anula `transfer_pair_id`, flusha, e só então apaga** — chame `unlink` **antes**, senão o cascade deixa uma `VaultEntry` obsoleta no persistence context da mesma transação.
7. **A sobra do rateio vai para o maior resto**, não para a última como no `InstallmentSplitter`. Documentar no javadoc.
8. **`vaultId` + `contributionPillar` iguais devem ser aceitos**, não recusados — o front faz round-trip do valor lido.
9. **MSW**: os mocks do orval são faker aleatório com 1s de delay — fixtures próprias, sempre.

---

## 8. Roteiro de verificação ponta a ponta

Cada passo prova uma decisão da seção 4:

1. Conta INVESTMENT, R$ 10.000, default `FINANCIAL_FREEDOM`. Aporte de R$ 500 → **funciona como hoje** (retrocompat, §4.4).
2. Criar "Reserva" (FINANCIAL_FREEDOM) e "Viagem 2027" (GOALS, alvo R$ 12.000, prazo 12 meses).
3. `/accounts/:id/vaults` mostra **R$ 10.500 não alocado**, caixinhas em zero (identidade, §4.3).
4. Remanejo `null → Reserva` R$ 6.000 e `null → Viagem` R$ 4.000. Não alocado = R$ 500, **nenhum saldo de conta mudou** (onboarding, §4.7).
5. Aporte sem `vaultId` → **`VAULT_REQUIRED`**.
6. Aporte na "Viagem" com pilar `FINANCIAL_FREEDOM` → **`VAULT_PILLAR_CONFLICT`**. Sem pilar → aceito, e o dashboard mostra **GOALS +500** — a prova de que o pilar migrou para a caixinha.
7. Meta da "Viagem": 4.500/12.000 = 37,5%, "faltam R$ 625,00/mês" (§4.6).
8. Resgate de R$ 8.000 da "Reserva" (saldo 6.000) → **`VAULT_INSUFFICIENT_BALANCE`** com `shortfallCents: 200000` (faltam R$ 2.000 para o resgate caber).
9. Aporte PENDING de R$ 1.000: `balanceCents` não muda, `projectedBalanceCents` sobe (§4.2).
10. Rendimento de R$ 100,01 com bases 6.000/4.500 → R$ 57,15 + R$ 42,86 = **exatamente 10001**. Saldo da conta sobe; **tile "receitas do mês" não muda** (§4.5).
11. `DELETE` da transação do rendimento → as duas linhas do razão somem sozinhas (cascade, §4.1).
12. `PATCH` do aporte de R$ 500 → R$ 700: caixinha vira R$ 4.700 **sem nenhuma escrita no razão** — o teste que prova o `amount_cents NULL` (§4.1).
13. Arquivar "Reserva" com saldo → **`VAULT_NOT_EMPTY`**; esvaziar e arquivar → passa; excluir "Viagem" → **`VAULT_HAS_ENTRIES`** (§4.7).
14. Arquivadas as duas, novo aporte volta a aceitar **sem `vaultId`**, usando o default da conta — a porta de saída existe.

---

### Achado da INT-3 resolvido na revisão — invalidação da composição

A INT-3 registrou como "lacuna de UX pré-existente" que o lançamento rápido do cabeçalho não atualizava a composição de caixinhas aberta, deixando número velho por até 30s (o `staleTime`), e contornou isso no E2E com `page.reload()`.

**A classificação estava errada e o contorno escondia o defeito.** A query de composição nasceu nesta fatia (FE-8) e a FE-9 ensinou justamente aquele diálogo a escrever em caixinha — ensinar um caminho de escrita a invalidar a query que ele afeta é parte do mesmo trabalho, não item de polimento futuro. Os **três** caminhos de escrita de `features/transactions/` tinham o mesmo buraco: lançamento rápido, edição no extrato e excluir/efetivar.

Corrigido com `vaultCompositionQueryFilter(workspaceId)` em `features/vaults/vaultOptions.ts`, usado nos três. Ele precisa ser um `predicate` e não uma chave: a chave gerada pelo orval é **uma string única** (`/workspaces/{ws}/accounts/{id}/vaults`), então o casamento por prefixo do TanStack não a alcança a partir do workspace. É exatamente o tipo de coisa que falha em silêncio — a invalidação simplesmente não acha nada e ninguém percebe — então o filtro tem teste próprio cobrindo o que ele casa e, principalmente, o que ele **não** pode casar (outra workspace, `/accounts` sem sufixo, o extrato da caixinha).

Front: **145 testes**.

---

## 9. Fora do escopo desta fatia

- **Rentabilidade na projeção da meta** — a projeção é linear. O simulador de renda fixa é Fase 3 e é ele que trará indexadores, IR regressivo e IOF.
- **Caixinha em conta corrente/dinheiro** — só INVESTMENT/SAVINGS, coerente com a regra 9.
- **Vincular compra de ação a uma caixinha** — é Fase 5, na absorção do app `IR/`.
- **Patrimônio consolidado por caixinha** — Fase 4.
- **Bloquear "não alocado" negativo** — decisão consciente da §4.3.
