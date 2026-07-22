# Prompt para o Claude Design — App de Controle de Ativos

Copie e cole o texto abaixo no Claude Design:

---

Crie um mock de aplicativo web de **controle de custo de aquisição de investimentos** (Ações, FIIs, ETFs brasileiros e Stocks/REITs/ETFs americanos), substituindo uma planilha Excel existente. O app deve ser em português (pt-BR), com visual limpo e moderno, voltado ao investidor pessoa física que precisa apurar preço médio e ganho de capital para o Imposto de Renda.

## Estrutura de telas

**1. Dashboard (Painel de Posição)**
- Cards de resumo por ativo: Quantidade Total Atual, Preço Médio (PM) Atual, Custo Total Atual.
- Toggle/comparativo entre "Posição Hoje" e "Posição na Data de Referência" (ex.: 31/12 do ano anterior, usada na declaração de IRPF), com Quantidade, PM e Custo de Aquisição em cada data.
- Para ativos dos EUA, exibir os valores em dupla moeda: USD e BRL lado a lado.
- Seletor de ativo (dropdown com busca) e separação visual entre carteira Brasil e carteira Exterior.

**2. Lançamento de Ordens (formulário)**
Campos: Ativo (ticker), Tipo de Ordem, Data, Quantidade, Valor da Ordem.
Tipos de ordem (dropdown): Compra, Venda, Desdobramento, Grupamento, Amortização, Bonificação.
Para ativos EUA, o Valor da Ordem é informado em USD e o app converte automaticamente para BRL (mostrar a cotação usada e a data da cotação como feedback no formulário).

**3. Histórico de Ordens (tabela por ativo)**
Colunas: #, Ativo, Ordem, Data, Quantidade, Valor da Ordem, Valor por Ação, Quantidade Acumulada, PM Acumulado, Ganho na Venda, Imposto (GCAP). Para ativos EUA, colunas duplicadas em USD e BRL.
Linhas de venda com lucro destacadas (badge do valor do imposto); vendas com prejuízo sem imposto.

**4. Tela/Modal de Apuração de Ganho de Capital**
Lista de vendas do período com lucro/prejuízo por operação e imposto devido, agrupado por mês.

## Lógica de negócio (regras que o app deve refletir)

**Quantidade Acumulada** (recalculada a cada ordem, por ativo):
`Compras − Vendas + Desdobramentos − Grupamentos + Bonificações` (Amortização não altera quantidade).

**Preço Médio de Aquisição (PM)** — regra por tipo de ordem:
- **Compra** ou **Bonificação**: média ponderada → `(PM anterior × Qtd anterior + Valor da Ordem) ÷ Nova quantidade`
- **Desdobramento** ou **Grupamento**: custo total preservado → `(PM anterior × Qtd anterior) ÷ Nova quantidade`
- **Amortização** (comum em FIIs): reduz o PM → `PM anterior − (Valor ÷ Quantidade)`
- **Venda**: PM **não muda** (regra fiscal brasileira)
- Primeira ordem do ativo: `Total das compras ÷ Quantidade comprada`

**Ganho na Venda**: `Quantidade vendida × (Preço de venda por ação − PM)`. Só existe em ordens de Venda.

**Imposto sobre Ganho de Capital (GCAP)** — incide apenas quando há lucro:
- Ações e Units/ETFs brasileiros: **15%**
- FIIs (tickers final 11 que não são Units/ETFs): **20%**
- Ativos no exterior: **15%**
- Prejuízo: sem imposto (exibir "—" ou badge neutro).

**Conversão cambial (ativos EUA)** — cada ordem é convertida pela PTAX da data:
- Compras usam a **PTAX de venda**; vendas/amortizações/bonificações usam a **PTAX de compra** (regra da Receita Federal).
- Se a data cair em fim de semana/feriado, usar a cotação do próximo dia útil (fallback D+1 até D+3).
- O PM é mantido em paralelo nas duas moedas: PM em USD e PM em BRL.

**Validações do formulário**:
- Tipo de ordem restrito à lista fixa acima.
- Ticker validado contra a carteira (BR ou EUA).
- Venda não pode exceder a quantidade acumulada disponível.

## Dados de exemplo para popular o mock

Ativo BR: ITSA4 — Compra 100 un. a R$ 9,50 (jan), Compra 100 un. a R$ 10,50 (mar) → PM R$ 10,00; Venda 50 un. a R$ 12,00 (jun) → ganho R$ 100,00, imposto R$ 15,00.
Ativo FII: MXRF11 — Compra 200 cotas a R$ 10,00; Amortização de R$ 0,20/cota → PM cai para R$ 9,80; venda futura tributada a 20%.
Ativo EUA: AAPL — Compra 10 un. a US$ 150,00 (PTAX venda 5,10 → R$ 7.650,00); Venda 5 un. a US$ 180,00 (PTAX compra 4,95) → ganho apurado em BRL, imposto 15%.

## Estilo

Design system sóbrio (tons neutros com um accent), tipografia legível para números (tabular), tabelas densas mas escaneáveis, badges de status para tipos de ordem (compra verde, venda vermelha, eventos corporativos em azul/roxo), responsivo, dark mode.

---
