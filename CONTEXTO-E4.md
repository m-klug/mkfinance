# Contexto para execução da E4 / INT-1 — MK Finance

> **Leia este arquivo primeiro.** Handoff autocontido para iniciar a Etapa E4 (INT-1, integração & entrega) num chat novo.
> Detalhamento: `PLANEJAMENTO.md` (verdade de negócio) e `DESENVOLVIMENTO-MVP.md` (fichas/convenções, ficha INT-1 em §4).
> Atualizado em 23/07/2026, após a E3 concluída. Substituiu o `CONTEXTO-E3.md`, removido do repositório (recuperável no git).

## 1. O projeto

App de controle financeiro pessoal, **self-hosted** (Proxmox + LXC, WireGuard + DNS interno). Uso pessoal, modelado como **multiusuário por espaço (`Workspace`)**. Despesas organizadas pelo **método dos 6 pilares** com alvos fixos: Custos Fixos 30, Conforto 10, Metas 20, Prazeres 10, Liberdade Financeira 25, Conhecimento 5.

### 1.1 Repositório e stack

Monorepo. Branch `main`, remote `git@github.com:m-klug/mkfinance.git` (**privado**, confirmado).

| Caminho | Stack |
|---|---|
| `mk-finance/api/` | Java 21 + Spring Boot 3.5 + Maven (`./mvnw`), Flyway, pacote `br.com.mkfinance` |
| `mk-finance/web/` | Vite 6 + React 19 + TS estrito, Tailwind 4, shadcn/ui, TanStack Query, i18n pt-BR, orval + MSW, Recharts (code-split) |
| `mk-finance/contracts/openapi.yaml` | **O contrato** — fonte única entre back e front |
| `mk-finance/docker/` | compose (caddy, api, web, postgres, backup) |
| `.claude/agents/` | personas: `java-architect`, `spring-boot-engineer`, `react-specialist` |

Ambiente verificado: Java 21.0.5, Node 24.14.1, Docker rodando. Sem Postgres local (Testcontainers no back; MSW no dev do front). Rodar o app hoje: `cd mk-finance/web && npm run dev` (localhost:5173, contra mocks).

## 2. Estado atual: E0–E3 concluídas (gate verde)

Gate da E3 rodado sobre `main` integrada (commit `5218f78`/`860207f`):

| Eixo | Resultado |
|---|---|
| `./mvnw verify` | **187 testes**, 0 falhas, BUILD SUCCESS |
| `npm test` | **74 testes**, 13 arquivos |
| `npm run build` | verde (aviso de chunk >500 kB — ver §5) |
| `docker compose config -q` | OK |
| Percentuais de alocação (cenário canônico) | ✅ conferidos à mão (ver §3) |

**Backend completo do MVP:** auth/workspaces (E1), contas/categorias/pilares (E1), transações/transferências/aportes (BE-3), cartão/faturas/parcelas/pagamento/estorno (BE-4 + refinos), dashboard/alocação/CSV (BE-5).
**Frontend completo do MVP:** shell/tema/i18n/auth (E1), contas/categorias/lançamento/extrato (FE-2), cartão/fatura/estorno/editar-transferência (FE-3), dashboard com gráficos (FE-4).

Migrações aplicadas: `V1`, `V2`, `V7` (BE-3), `V9` (BE-4). V3–V6, V8, V10–V12 vagas — **não renumere**.

## 3. Cenário canônico de alocação (gate da E3, conferido à mão)

Receita R$ 5.000,00 (500.000 centavos), alvos 30/10/20/10/25/5:

| Pilar | Gasto (¢) | % | Alvo | Composição |
|---|---|---|---|---|
| FIXED_COSTS | 150.000 | 30,0 | 30 | compra cartão (pagamento de fatura **excluído**, R2) |
| COMFORT | 0 | 0,0 | 10 | — |
| GOALS | 40.000 | 8,0 | 20 | aporte (R9) |
| PLEASURES | 75.000 | 15,0 | 10 | 50.000 cartão + 25.000 direto |
| FINANCIAL_FREEDOM | 100.000 | 20,0 | 25 | aporte (R9) |
| KNOWLEDGE | 10.000 | 2,0 | 5 | despesa direta |

Fora de tudo: transferência R$ 2.000 entre contas correntes (R4). `expenseCents=235.000` (competência, sem aportes); saldo s/ investimentos 205.000, c/ investimentos 345.000 (Δ 140.000 = os aportes). Coberto por `PillarAllocationCalculatorTest.Canonical` + `DashboardIntegrationTest`.

## 4. Convenções invioláveis (herdadas por toda ficha)

1. **Código 100% em inglês** com glossário PT→EN de `PLANEJAMENTO.md` §3.3.
2. **Dinheiro é `long` em centavos**, sempre.
3. **Datas de negócio** em `LocalDate` (`America/Sao_Paulo`); `TIMESTAMPTZ` só para auditoria.
4. **Isolamento por workspace**: toda query filtra por `workspace_id`.
5. **Erros**: código de máquina em `ErrorResponse{code, params?, fieldErrors?}`. Backend nunca devolve texto pt-BR; o front traduz.
6. **i18n**: zero string hardcoded em JSX; chaves em `src/i18n/locales/pt-BR/common.json`, agrupadas por área (`nav.*`, `cards.*`, `statement.*`, `dashboard.*`, `errors.*`…).
7. **Migrações Flyway append-only.**
8. **Só o `java-architect` edita o contrato.**
9. **Nunca editar `web/src/lib/api/generated/**`.**
10. **Lombok nas entidades JPA, `record` nos DTOs.**
11. **Selects nativos `<select>`** — não introduzir o `Select` do shadcn em forms novos (decisão do usuário).

## 5. A Etapa E4 — INT-1 (sequencial, um agente `java-architect`)

Ficha completa em `DESENVOLVIMENTO-MVP.md` §4 (INT-1). É **sequencial** — uma frente só. Passos:

1. **Trocar MSW pela API real** — `VITE_API_MOCK=false` no dev/prod; MSW permanece só para testes. **Ponto de integração mais delicado, ver §6.**
2. **Revisar divergências contrato × implementação** — comparar springdoc `/v3/api-docs` com `contracts/openapi.yaml`. O contrato é atualizado para refletir a realidade e o cliente regenerado (`npm run api:generate`). O contrato já sofreu uma passada de sincronização na E2/E3, então as divergências devem ser poucas.
3. **Smoke E2E** — Playwright: registro → conta → lançamento → compra parcelada → pagamento de fatura → dashboard confere. O cenário canônico (§3) é um bom roteiro.
4. **PWA** — manifest, service worker via `vite-plugin-pwa`, instalável.
5. **Build Docker final** — `docker compose up` completo com Caddy TLS interno; backup testado com **restore real** de um dump.
6. **Acessibilidade básica + i18n** — nenhuma chave faltando.

Aceite: `docker compose up -d` numa máquina limpa serve o app em HTTPS; E2E verde; dump de backup restaurável.

## 6. Ponto crítico da integração — CSRF e context-path

Hoje o front roda contra MSW **por design**; a ligação real nunca foi exercitada. Ao ligar de verdade:
- Backend usa `context-path: /api` (config da E1). O `baseURL` do cliente/orval precisa refletir isso.
- **Fluxo CSRF SPA**: `CookieCsrfTokenRepository.withHttpOnlyFalse()`. O front precisa: GET inicial → recebe cookie `XSRF-TOKEN` → ecoa em header `X-XSRF-TOKEN` nas mutations. Sessão em cookie `MK_FINANCE_SESSION`. CORS liberado para `http://localhost:5173` no dev.
- Este é o ponto onde bugs de integração vão aparecer (401/403 inesperados). Testar cedo.

## 7. Dívidas e notas conhecidas (não bloqueiam, mas trate na INT-1)

- **Bundle inicial ~561 kB** (aviso >500 kB). A FE-4 já isolou o Recharts num chunk assíncrono (núcleo de 348 kB fora do inicial); o inicial cresceu só com código de feature. Se quiser silenciar o aviso, ajuste `build.chunkSizeWarningLimit` ou fatie mais — cosmético.
- **`TransferUpdateHandler` já foi aposentado** (a lógica vive no `TransferService`); nada a fazer.
- **Estorno de pagamento**: `DELETE /statements/{id}/payments/{paymentId}`; excluir a despesa pelo `/transactions` devolve `TRANSACTION_LOCKED_BY_PAYMENT` (aponta para a tela da fatura). Sem estorno via extrato — por desenho.
- **`BusinessClock`** foi promovido para `application/common` na BE-5.

## 8. Armadilhas operacionais (para quem orquestrar com agentes)

1. **Worktrees de agente nascem do commit em que a sessão começou, não do `main` atual.** Instrua `git rebase main` explícito antes de codar. (INT-1 é sequencial e única, então o risco é menor, mas vale.)
2. **Agentes longos podem cair** — por infra (queda de API/stall) ou por **limite de sessão da conta** (aconteceu na E3, resetou às 3:10). Em ambos os casos: commit incremental salva o trabalho; retomar via `SendMessage` ao agentId continua do transcript. **Instrua commits incrementais desde o início.**
3. **Merge de duas frentes de front que tocam `App.tsx`/`common.json`**: o 3-way do git resolve o aditivo sozinho, mas pode deixar um **import órfão** (ex.: `Placeholder` ficou sem uso após ambas trocarem placeholders por telas reais → `tsc` estrito quebrou o build embora os testes passassem). **Sempre rode `npm run build`, não só `npm test`, após merge de front.**
4. **orval gera `PATCH`, não `PUT`** — handler de teste com `http.put` falha em silêncio (`onUnhandledRequest: "bypass"`).
5. **`queryClient.setQueryData(key, undefined)` é no-op no TanStack v5** — use `removeQueries`.
6. **Mocks do orval respondem faker aleatório com 1s de delay** — use `financeFixtures`/`dashboardFixtures` + `server.use(...)`.

## 9. Git e backup

- `main` está à frente de `origin/main` (o usuário fez um push de backup durante a E3; commits posteriores da E3 seguem locais).
- **`git push` está bloqueado pela trava de segurança** em `.claude/settings.json` (`deny`), e o classificador impede editá-la para afrouxar — **o push precisa ser feito pelo usuário**. Antes de fechar a INT-1, vale sincronizar `origin`.

## 10. Gate da E4 (aceite final do INT-1)

```bash
cd mk-finance/api && ./mvnw verify
cd mk-finance/web && npm test && npm run build
POSTGRES_PASSWORD=x docker compose -f mk-finance/docker/docker-compose.yml config -q
```
Mais: `docker compose up -d` numa máquina limpa serve HTTPS funcional; smoke E2E Playwright verde; restore real de um dump de backup.
