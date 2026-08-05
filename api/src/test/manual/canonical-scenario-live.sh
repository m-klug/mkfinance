#!/usr/bin/env bash
# INT-2 — reconferência À MÃO do cenário canônico da E3 contra a stack real (HTTP + CSRF),
# depois da mudança para SETTLED-only (BE-6). Herdado do gate da E6 e adiado até aqui.
# Acrescenta duas provas novas: PENDING não move a alocação, e o comportamento da janela do extrato.
set -euo pipefail
API=http://localhost:8080/api
J=$(mktemp); trap 'rm -f "$J"' EXIT
EMAIL="int2-$(date +%s)@example.com"

csrf() { grep -o 'XSRF-TOKEN[[:space:]]*[^[:space:]]*$' "$J" | awk '{print $NF}' | tail -1; }
post() { curl -s -b "$J" -c "$J" -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(csrf)" -X POST -d "$2" "$API$1"; }
get()  { curl -s -b "$J" -c "$J" "$API$1"; }
jqv()  { node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>{try{const o=JSON.parse(s);console.log(eval("o"+process.argv[1]))}catch(e){console.error("PARSE FAIL:",s.slice(0,300));process.exit(1)}})' "$1"; }

echo "== registrando usuário na stack real =="
# o SPA sempre faz uma leitura antes do primeiro POST; é ela que semeia o cookie XSRF-TOKEN
curl -s -c "$J" "$API/auth/me" >/dev/null
WS=$(post "/auth/register" "{\"name\":\"INT2\",\"email\":\"$EMAIL\",\"password\":\"senha12345\"}" | jqv '.workspaces[0].id')
echo "   workspace $WS"
W="/workspaces/$WS"

acc()  { post "$W/accounts" "{\"name\":\"$1\",\"type\":\"$2\"}" | jqv '.id'; }
cat_() { post "$W/categories" "$1" | jqv '.id'; }

CHECKING=$(acc Conta CHECKING); CHECKING2=$(acc "Conta 2" CHECKING); INVEST=$(acc Corretora INVESTMENT)
INC=$(cat_ '{"name":"Salario","type":"INCOME"}')
PLE=$(cat_ '{"name":"Lazer","type":"EXPENSE","pillar":"PLEASURES"}')
KNO=$(cat_ '{"name":"Cursos","type":"EXPENSE","pillar":"KNOWLEDGE"}')
FIX=$(cat_ '{"name":"Moradia","type":"EXPENSE","pillar":"FIXED_COSTS"}')

tx() { post "$W/transactions" "{\"accountId\":\"$1\",\"categoryId\":\"$2\",\"type\":\"$3\",\"amountCents\":$4,\"date\":\"$5\",\"description\":\"$6\"${7:-}}" >/dev/null; }
echo "== cenário canônico (março/2026) =="
tx "$CHECKING" "$INC" INCOME  500000 2026-03-15 Salario
tx "$CHECKING" "$PLE" EXPENSE  25000 2026-03-15 Lazer
tx "$CHECKING" "$KNO" EXPENSE  10000 2026-03-15 Curso

CARD=$(post "$W/cards" "{\"name\":\"Cartao\",\"limitCents\":1000000,\"closingDay\":10,\"dueDay\":20,\"defaultPaymentAccountId\":\"$CHECKING\"}" | jqv '.id')
post "$W/cards/$CARD/purchases" "{\"categoryId\":\"$FIX\",\"description\":\"Aluguel\",\"totalAmountCents\":150000,\"purchaseDate\":\"2026-03-05\"}" >/dev/null
post "$W/cards/$CARD/purchases" "{\"categoryId\":\"$PLE\",\"description\":\"Show\",\"totalAmountCents\":50000,\"purchaseDate\":\"2026-03-05\"}" >/dev/null
ST=$(get "$W/cards/$CARD/statements?from=2026-03&to=2026-03" | jqv '[0].id')
post "$W/statements/$ST/payments" "{\"accountId\":\"$CHECKING\",\"amountCents\":120000,\"paymentDate\":\"2026-03-10\"}" >/dev/null

post "$W/transfers" "{\"fromAccountId\":\"$CHECKING\",\"toAccountId\":\"$INVEST\",\"amountCents\":40000,\"date\":\"2026-03-15\",\"description\":\"Aporte metas\",\"contributionPillar\":\"GOALS\"}" >/dev/null
post "$W/transfers" "{\"fromAccountId\":\"$CHECKING\",\"toAccountId\":\"$INVEST\",\"amountCents\":100000,\"date\":\"2026-03-15\",\"description\":\"Aporte liberdade\",\"contributionPillar\":\"FINANCIAL_FREEDOM\"}" >/dev/null
post "$W/transfers" "{\"fromAccountId\":\"$CHECKING\",\"toAccountId\":\"$CHECKING2\",\"amountCents\":200000,\"date\":\"2026-03-15\",\"description\":\"Transferencia simples\"}" >/dev/null

echo
echo "== números da E3 (alvo · gasto · %) =="
get "$W/dashboard/pillar-allocation?month=2026-03" > /tmp/int2-alloc.json
node -e '
const r=require("/tmp/int2-alloc.json");
const exp={FIXED_COSTS:[30,150000,30],COMFORT:[10,0,0],GOALS:[20,40000,8],
           PLEASURES:[10,75000,15],FINANCIAL_FREEDOM:[25,100000,20],KNOWLEDGE:[5,10000,2]};
let bad=0;
if(r.incomeCents!==500000){console.log("FALHA incomeCents="+r.incomeCents);bad++;}
else console.log("OK    incomeCents = 500000");
for(const p of r.pillars){ const [t,s,a]=exp[p.pillar];
  const ok = p.targetPercent===t && p.spentCents===s && Math.abs((p.actualPercent??0)-a)<0.001;
  console.log((ok?"OK    ":"FALHA ")+p.pillar.padEnd(18)+" alvo="+p.targetPercent+" gasto="+p.spentCents+" real="+p.actualPercent);
  if(!ok){bad++;console.log("        esperado alvo="+t+" gasto="+s+" real="+a);} }
process.exit(bad?1:0)'
echo

echo "== SETTLED-only: uma despesa PENDING não pode mover a alocação =="
post "$W/transactions" "{\"accountId\":\"$CHECKING\",\"categoryId\":\"$PLE\",\"type\":\"EXPENSE\",\"amountCents\":99999,\"date\":\"2026-03-20\",\"description\":\"Pendente\",\"status\":\"PENDING\"}" >/dev/null
AFTER=$(get "$W/dashboard/pillar-allocation?month=2026-03" | jqv '.pillars.find(p=>p.pillar==="PLEASURES").spentCents')
[ "$AFTER" = "75000" ] && echo "OK    PLEASURES continua 75000 com a pendente lançada" \
                       || echo "FALHA PLEASURES virou $AFTER (a pendente entrou na alocação)"

echo
echo "== alvos versionados: editar hoje não pode reescrever março =="
curl -s -b "$J" -c "$J" -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $(csrf)" -X PUT \
  -d '{"allocations":[{"pillar":"FIXED_COSTS","targetPercent":50},{"pillar":"COMFORT","targetPercent":10},{"pillar":"GOALS","targetPercent":10},{"pillar":"PLEASURES","targetPercent":10},{"pillar":"FINANCIAL_FREEDOM","targetPercent":15},{"pillar":"KNOWLEDGE","targetPercent":5}]}' \
  "$API$W/pillar-allocations" >/dev/null
MAR=$(get "$W/dashboard/pillar-allocation?month=2026-03" | jqv '.pillars.find(p=>p.pillar==="FIXED_COSTS").targetPercent')
NOW=$(get "$W/pillar-allocations" | jqv '.find(p=>p.pillar==="FIXED_COSTS").targetPercent')
[ "$MAR" = "30" ] && echo "OK    março preserva o alvo antigo de FIXED_COSTS (30)" || echo "FALHA março virou $MAR"
[ "$NOW" = "50" ] && echo "OK    mês corrente já usa o alvo novo (50)"              || echo "FALHA mês corrente = $NOW"

echo
echo "== soma != 100 é rejeitada =="
CODE=$(curl -s -o /tmp/int2-err.json -w '%{http_code}' -b "$J" -c "$J" -H 'Content-Type: application/json' \
  -H "X-XSRF-TOKEN: $(csrf)" -X PUT \
  -d '{"allocations":[{"pillar":"FIXED_COSTS","targetPercent":50},{"pillar":"COMFORT","targetPercent":10},{"pillar":"GOALS","targetPercent":10},{"pillar":"PLEASURES","targetPercent":10},{"pillar":"FINANCIAL_FREEDOM","targetPercent":15},{"pillar":"KNOWLEDGE","targetPercent":99}]}' \
  "$API$W/pillar-allocations")
ERR=$(node -e 'console.log(require("/tmp/int2-err.json").code)')
[ "$CODE" = "400" ] && [ "$ERR" = "PILLAR_TARGETS_MUST_SUM_100" ] \
  && echo "OK    400 PILLAR_TARGETS_MUST_SUM_100" || echo "FALHA http=$CODE code=$ERR"

echo
echo "== recorrência: materialização pela leitura do extrato =="
TODAY=$(date +%Y-%m-%d); MONTH=$(date +%Y-%m); FIRST="$MONTH-01"; LAST=$(date -v+1m -v1d -v-1d +%Y-%m-%d 2>/dev/null || date -d "$(date +%Y-%m-01) +1 month -1 day" +%Y-%m-%d)
post "$W/recurrences" "{\"type\":\"INCOME\",\"accountId\":\"$CHECKING\",\"categoryId\":\"$INC\",\"amountCents\":700000,\"description\":\"Salario mensal\",\"frequency\":\"MONTHLY\",\"dayOfMonth\":25,\"startDate\":\"$FIRST\"}" >/dev/null
N_DEFAULT=$(get "$W/transactions?from=$FIRST&to=$TODAY" | jqv '.content.filter(t=>t.description==="Salario mensal").length')
N_FULL=$(get "$W/transactions?from=$FIRST&to=$LAST"   | jqv '.content.filter(t=>t.description==="Salario mensal").length')
echo "   janela padrão do extrato ($FIRST..$TODAY): $N_DEFAULT ocorrência(s)"
echo "   mês inteiro            ($FIRST..$LAST): $N_FULL ocorrência(s)"
[ "$N_FULL" = "1" ] && echo "OK    a leitura do extrato materializou a ocorrência do dia 25" \
                    || echo "FALHA o extrato não materializou (esperava 1, veio $N_FULL)"

echo
echo "== idempotência: reler não pode duplicar =="
get "$W/transactions?from=$FIRST&to=$LAST" >/dev/null
get "$W/calendar?month=$MONTH" >/dev/null
AGAIN=$(get "$W/transactions?from=$FIRST&to=$LAST" | jqv '.content.filter(t=>t.description==="Salario mensal").length')
[ "$AGAIN" = "1" ] && echo "OK    após reler extrato + calendário, continua 1" || echo "FALHA duplicou: $AGAIN"
