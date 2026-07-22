# Contexto para desenvolvimento do backend — Controle de Custo de Aquisição (IR)

> Gerado a partir do estado atual do front-end (mock funcional em React) para servir de handoff a uma conversa focada em backend. Cole este arquivo (ou peça para o Claude ler `backend-context.md`) no início da nova conversa.

## 1. O que é o app

Substitui uma planilha Excel de controle de custo de aquisição de investimentos (Ações, FIIs, ETFs BR e Stocks/REITs/ETFs EUA) para apuração de **preço médio** e **ganho de capital (IR)**. Usuário pessoa física, app em pt-BR.

Hoje existe **apenas o front-end**, rodando 100% client-side com dados mockados em memória (Zustand). Não há API, banco de dados, autenticação ou persistência real. Este documento existe para levar essa lógica de negócio (hoje em TypeScript, em `app/src/lib/calc.ts` e `app/src/lib/ptax.ts`) para um backend real.

## 2. Stack do front-end (referência, não migrar)

React 19 + Vite + TypeScript, React Router 7, Zustand 5 (estado em memória, sem persistência), Tailwind 4 + shadcn/radix (`app/src/components/ui/*`), sonner (toasts). Não há chamadas HTTP em lugar nenhum do código — tudo é calculado no cliente a partir do array `ordens` guardado no store.

## 3. Modelo de domínio (hoje em `app/src/lib/types.ts`)

```ts
type Mercado = "BR" | "EUA"
type CategoriaAtivo = "ACAO" | "FII" | "ETF" | "UNIT" | "STOCK" | "REIT" | "ETF_EUA"
type TipoOrdem = "COMPRA" | "VENDA" | "DESDOBRAMENTO" | "GRUPAMENTO" | "AMORTIZACAO" | "BONIFICACAO"

Ativo { ticker, nome, mercado, categoria }

Ordem {
  id, ticker, tipo, data (ISO yyyy-mm-dd),
  quantidade, valorOrdem, // moeda nativa: BRL se BR, USD se EUA
  ptax?: { taxa, tipo: "compra"|"venda", dataCotacao, fallback } // calculado, não persistido pelo usuário
}
```

Esses dois (`Ativo`, `Ordem`) são as entidades "de verdade" a persistir. Tudo mais (`LinhaLedger`, `PosicaoAtivo`) é **derivado**, recalculado a cada leitura a partir das ordens — não é estado próprio. Isso é uma decisão de design a levar para o backend: manter as ordens como fonte da verdade (event-sourced) e recalcular o ledger sob demanda (ou cachear/materializar, mas as ordens continuam sendo a fonte).

Hoje não existe conceito de usuário/carteira — `ATIVOS` e `ORDENS_SEED` são globais e fixos (`app/src/lib/mockData.ts`). Um backend real precisa decidir: multiusuário? múltiplas carteiras por usuário? Isso não está definido em lugar nenhum do front hoje.

## 4. Regras de negócio a migrar (hoje em `app/src/lib/calc.ts`)

Esta é a parte principal a portar para o servidor — hoje roda 100% no navegador.

### 4.1 Ordenação das ordens
Ordens de um ativo são processadas em ordem cronológica; em caso de empate na data, pela ordem de criação (id/sequência de inserção).

### 4.2 Quantidade acumulada (por tipo de ordem)
- COMPRA / BONIFICACAO: `qtd += quantidade`
- VENDA: `qtd -= quantidade`
- DESDOBRAMENTO: `qtd += quantidade`
- GRUPAMENTO: `qtd -= quantidade`
- AMORTIZACAO: não altera quantidade

### 4.3 Preço médio (PM) — regra por tipo de ordem
- **COMPRA**: primeira compra do ativo → `PM = valorOrdem / quantidade`; senão média ponderada: `PM = (PManterior × Qtdanterior + valorOrdem) / novaQtd`
- **BONIFICACAO**: mesma fórmula de média ponderada da compra (valorOrdem tipicamente 0, dilui o PM)
- **DESDOBRAMENTO / GRUPAMENTO**: custo total preservado → `PM = (PMAnterior × QtdAnterior) / novaQtd`
- **AMORTIZACAO**: reduz o PM → `PM = PMAnterior − (valorOrdem / base)`, onde `base = quantidade da ordem, ou qtdAnterior se 0, ou 1` (fallback de divisão por zero)
- **VENDA**: PM não muda (regra fiscal brasileira)

### 4.4 Ganho na venda
Só existe em ordens de VENDA: `ganho = quantidade × (valorPorAção − PMAnterior)`, onde `valorPorAção = valorOrdem / quantidade`.

### 4.5 Alíquota de imposto sobre ganho de capital (GCAP)
```
aliquotaGcap(ativo):
  se mercado == EUA        → 15%
  senão se categoria == FII → 20%
  senão (Ação/ETF/Unit BR)  → 15%
```
Imposto só incide se houve **lucro** (`ganho > 0`) em uma VENDA: `imposto = ganho × aliquota`. Prejuízo → sem imposto (não é feita nenhuma compensação de prejuízos entre operações/meses — ponto em aberto, ver seção 7).

### 4.6 Conversão cambial (ativos EUA) — PTAX
Cada ordem em ativo americano é convertida pela PTAX do dia:
- **COMPRA** usa PTAX de **venda** do dólar
- **VENDA / AMORTIZACAO / BONIFICACAO** usam PTAX de **compra** do dólar
(regra da Receita Federal: `ptaxTipoParaOrdem` em `calc.ts`)

Se a data cai em fim de semana/feriado, usa a cotação do próximo dia útil, com fallback de até D+3 dias (`app/src/lib/ptax.ts`, `isDiaUtil`). Feriados hoje são só os fixos nacionais (lista fechada, sem cálculo de feriados móveis nem calendário oficial do BCB).

**Importante**: hoje a cotação PTAX é **inteiramente mockada** — `getPtax()` gera uma taxa determinística por hash da data (não é uma cotação real). Isso precisa virar uma integração real com a **API PTAX do Banco Central** (dados históricos de câmbio de compra/venda) no backend, incluindo cache/persistência das cotações já consultadas.

Para ativos EUA, o PM e o custo total são mantidos em paralelo nas duas moedas (USD nativo + BRL convertido na data de cada ordem — não é uma reconversão do PM final, é acumulado operação a operação).

### 4.7 Posição consolidada (`computePosicao`)
Dado um ativo e opcionalmente uma "data de corte" (`ateData`), filtra as ordens até aquela data (inclusive), roda o ledger completo, e retorna a última linha como posição: quantidade, PM, custo total (e equivalentes BRL para EUA). Usada tanto para "posição hoje" quanto "posição na data de referência do IRPF" (ex.: 31/12 do ano anterior).

### 4.8 Apuração de vendas por período (`apurarVendas`)
Para uma lista de ativos e um intervalo de datas, roda o ledger de cada ativo e filtra as linhas de VENDA dentro do intervalo, ordenadas por data. É a base da tela de Apuração de Ganho de Capital, agrupada por mês no front.

## 5. Telas e os dados que cada uma consome (referência para desenhar endpoints)

1. **Dashboard / Painel de Posição** (`app/src/pages/Dashboard.tsx`)
   Para cada ativo: posição "hoje" e posição na "data de referência" (input do usuário, default 31/12 do ano anterior). Totais agregados de custo por carteira (Brasil / Exterior, em BRL). Precisa: listar ativos do usuário + calcular posição em 2 datas por ativo.

2. **Lançamento de Ordens** (`app/src/pages/NovaOrdem.tsx`)
   Formulário: ticker (autocomplete restrito à carteira), tipo de ordem, data, quantidade, valor da ordem (USD se EUA). Mostra em tempo real: posição do ativo até a data escolhida (para validar venda) e preview da conversão PTAX (taxa, tipo, data da cotação, valor em BRL). **Validações client-side hoje que devem ser reforçadas no servidor**: ativo precisa existir na carteira; quantidade > 0; valor obrigatório (exceto 0 permitido em DESDOBRAMENTO/GRUPAMENTO/BONIFICACAO); venda não pode exceder quantidade acumulada disponível na data.

3. **Histórico de Ordens** (`app/src/pages/Historico.tsx`)
   Ledger completo (todas as linhas calculadas) de um ativo por vez — todas as colunas descritas na seção 4, incluindo colunas duplicadas USD/BRL para ativos EUA.

4. **Apuração de Ganho de Capital** (`app/src/pages/Apuracao.tsx`)
   Vendas de um ano-calendário inteiro, agrupadas por mês, com resultado (lucro/prejuízo) e imposto por operação e por mês, mais totais do ano. Seletor de ano é derivado dinamicamente dos anos em que existem vendas.

## 6. O que precisa ser adicionado que hoje não existe no front (é só mock)

- **Persistência real** de `Ativo` e `Ordem` (hoje: array em memória, perdido ao dar refresh).
- **Autenticação/usuário** — não existe conceito de login; tudo é uma carteira global fixa.
- **Cadastro de ativos** — hoje a lista de ativos (`ATIVOS`) é fixa/hardcoded; não há tela de "adicionar ativo à carteira". Decidir se o backend expõe um catálogo de tickers (BR/EUA) ou se o usuário cadastra livremente.
- **Cotações PTAX reais** via API do Banco Central, com cache/persistência (hoje mockado por hash determinístico).
- **Regra de feriados completa** (hoje só feriados fixos nacionais, sem feriados móveis nem o calendário real do BCB).
- **Edição/exclusão de ordens** — hoje só existe `addOrdem`; não há update nem delete no store.
- **Idempotência / concorrência** na inserção de ordens (hoje `id` é só um contador incremental em memória, `u1000`, `u1001`...).

## 7. Pontos de negócio em aberto (não resolvidos pelo front, decidir ao desenhar o backend)

- **Compensação de prejuízos**: hoje cada venda é tributada isoladamente (prejuízo não compensa lucro de outra venda/mês/ativo). A regra real da Receita Federal permite compensar prejuízos de meses anteriores dentro da mesma categoria de ativo — isso não está implementado em lugar nenhum e provavelmente deveria estar no backend.
- **Isenção de R$ 20.000/mês para ações** (vendas de ações em conjunto até R$20mil/mês são isentas de IR no Brasil) — não implementada no front; hoje toda venda com lucro é tributada a 15%/20% sem checar esse limite.
- **Multiusuário / múltiplas carteiras**: arquitetura de dados (um usuário, uma carteira vs. várias carteiras por usuário) não está definida.
- **Fonte de verdade para tickers/categorias**: se vier de um provedor de dados de mercado (B3, listas de FIIs etc.) ou cadastro manual.

## 8. Sugestão de escopo inicial de API (não vinculante, ponto de partida)

- `POST /ativos`, `GET /ativos` — CRUD de ativos da carteira
- `POST /ordens`, `GET /ordens?ticker=`, `PATCH/DELETE /ordens/:id` — CRUD de ordens (hoje só existe create)
- `GET /posicao?ticker=&data=` — posição consolidada de um ativo em uma data (equivalente a `computePosicao`)
- `GET /ledger?ticker=` — ledger completo de um ativo (equivalente a `buildLedger`)
- `GET /apuracao?ano=` — vendas do ano agrupadas por mês com imposto (equivalente a `apurarVendas`)
- `GET /ptax?data=&tipo=compra|venda` — cotação PTAX real (substitui o mock de `ptax.ts`)

Toda a lógica das seções 4.2–4.6 (cálculo de PM, quantidade, imposto, conversão cambial) deveria ser reimplementada no backend como fonte única de verdade — o front hoje faz esse cálculo client-side porque não tem outra opção (mock), mas em produção o cliente não deveria ser confiável para calcular imposto.
