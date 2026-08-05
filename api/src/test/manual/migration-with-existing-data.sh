#!/usr/bin/env bash
# INT-2 — prova que as migrações da Fase 2 (V13..V17) aplicam sobre um banco que JÁ TEM DADOS.
#
# Por que isso existe: o Flyway roda no boot dos testes, antes de qualquer registro de usuário, e o
# V2__seed.sql não semeia pillar_allocation. Resultado: em 261 testes o "UPDATE pillar_allocation
# SET effective_from" da V13 sempre pegou ZERO linha. O backfill e a troca de PK com linhas de
# verdade nunca foram exercitados. Este script fecha esse buraco sem envolver dado real de produção.
set -euo pipefail

MIG="$1"          # diretório das migrações
CID=""
cleanup() { [ -n "$CID" ] && docker rm -f "$CID" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "== subindo postgres:17-alpine (mesma major de produção) =="
CID=$(docker run -d -e POSTGRES_PASSWORD=migtest -e POSTGRES_DB=migtest postgres:17-alpine)
for _ in $(seq 1 60); do
  docker exec "$CID" pg_isready -U postgres -d migtest >/dev/null 2>&1 && break
  sleep 1
done

psql() { docker exec -i "$CID" psql -v ON_ERROR_STOP=1 -U postgres -d migtest "$@"; }
apply() { echo "   -> $1"; psql -q < "$MIG/$1"; }

echo "== estado PRÉ-Fase 2: aplicando V1, V2, V7, V9 =="
apply V1__schema.sql
apply V2__seed.sql
apply V7__transfer_direction.sql
apply V9__system_category_unique.sql

echo "== inserindo dados como o app teria criado (é isto que os testes nunca têm) =="
psql -q <<'SQL'
INSERT INTO app_user (id, name, email, password_hash)
VALUES ('11111111-1111-1111-1111-111111111111', 'Murilo', 'murilo@example.com', 'x');

-- dois workspaces, para provar que o backfill não mistura escopos
INSERT INTO workspace (id, name) VALUES
  ('22222222-2222-2222-2222-222222222222', 'Pessoal'),
  ('33333333-3333-3333-3333-333333333333', 'Casa');

INSERT INTO member (workspace_id, user_id, role) VALUES
  ('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111', 'OWNER'),
  ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'OWNER');

-- os 6 alvos por workspace, exatamente como o WorkspaceDefaultsSeeder cria no registro
INSERT INTO pillar_allocation (workspace_id, pillar, target_percent)
SELECT w.id, p.pillar, p.pct
FROM (VALUES
    ('22222222-2222-2222-2222-222222222222'::uuid),
    ('33333333-3333-3333-3333-333333333333'::uuid)) AS w(id),
  (VALUES ('FIXED_COSTS',30),('COMFORT',10),('GOALS',20),
          ('PLEASURES',10),('FINANCIAL_FREEDOM',25),('KNOWLEDGE',5)) AS p(pillar,pct);

INSERT INTO account (id, workspace_id, name, type)
VALUES ('44444444-4444-4444-4444-444444444444','22222222-2222-2222-2222-222222222222','Conta','CHECKING');

INSERT INTO category (id, workspace_id, name, type, pillar)
VALUES ('55555555-5555-5555-5555-555555555555','22222222-2222-2222-2222-222222222222','Aluguel','EXPENSE','FIXED_COSTS');

INSERT INTO transaction (id, workspace_id, account_id, category_id, type, amount_cents, date, description, status)
VALUES
 ('66666666-6666-6666-6666-666666666666','22222222-2222-2222-2222-222222222222','44444444-4444-4444-4444-444444444444','55555555-5555-5555-5555-555555555555','EXPENSE',150000,'2026-07-05','Aluguel julho','SETTLED'),
 ('77777777-7777-7777-7777-777777777777','22222222-2222-2222-2222-222222222222','44444444-4444-4444-4444-444444444444','55555555-5555-5555-5555-555555555555','EXPENSE',150000,'2026-08-05','Aluguel agosto','PENDING');
SQL

echo "   linhas antes da V13:"
psql -tAc "SELECT '     pillar_allocation=' || count(*) FROM pillar_allocation;
           SELECT '     transaction=' || count(*) FROM transaction;"

echo "== aplicando as migrações da Fase 2 =="
apply V13__pillar_allocation_versioning.sql
apply V14__recurrences.sql
apply V15__recurrence_occurrences.sql
apply V16__tags.sql
apply V17__transaction_tag.sql

echo
echo "== VERIFICAÇÕES =="
psql -tA <<'SQL'
\echo '-- backfill: toda linha preexistente virou 2000-01-01, nenhuma NULL'
SELECT CASE WHEN count(*) FILTER (WHERE effective_from = DATE '2000-01-01') = 12
             AND count(*) = 12
            THEN 'OK    12/12 linhas com effective_from = 2000-01-01'
            ELSE 'FALHA backfill: ' || count(*) || ' linhas, ' ||
                 count(*) FILTER (WHERE effective_from = DATE '2000-01-01') || ' com a data-piso'
       END FROM pillar_allocation;

\echo '-- PK virou a tripla (workspace_id, pillar, effective_from)'
SELECT CASE WHEN string_agg(a.attname, ',' ORDER BY k.ord) = 'workspace_id,pillar,effective_from'
            THEN 'OK    PK = (workspace_id, pillar, effective_from)'
            ELSE 'FALHA PK = ' || string_agg(a.attname, ',' ORDER BY k.ord) END
FROM pg_constraint c
JOIN LATERAL unnest(c.conkey) WITH ORDINALITY AS k(attnum, ord) ON TRUE
JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = k.attnum
WHERE c.conrelid = 'pillar_allocation'::regclass AND c.contype = 'p';

\echo '-- effective_from é NOT NULL'
SELECT CASE WHEN attnotnull THEN 'OK    effective_from NOT NULL' ELSE 'FALHA effective_from aceita NULL' END
FROM pg_attribute WHERE attrelid='pillar_allocation'::regclass AND attname='effective_from';

\echo '-- versionar de verdade: dá para inserir a MESMA (workspace,pilar) em outra competência'
INSERT INTO pillar_allocation (workspace_id, pillar, target_percent, effective_from)
VALUES ('22222222-2222-2222-2222-222222222222','FIXED_COSTS',35,DATE '2026-08-01');
SELECT CASE WHEN count(*) = 2 THEN 'OK    histórico coexiste (2 linhas para FIXED_COSTS)'
            ELSE 'FALHA esperava 2 linhas, achei ' || count(*) END
FROM pillar_allocation
WHERE workspace_id='22222222-2222-2222-2222-222222222222' AND pillar='FIXED_COSTS';
SQL

echo '-- duplicata exata precisa falhar (esperado: erro de PK)'
if docker exec -i "$CID" psql -q -v ON_ERROR_STOP=1 -U postgres -d migtest -c \
   "INSERT INTO pillar_allocation (workspace_id,pillar,target_percent,effective_from)
    VALUES ('22222222-2222-2222-2222-222222222222','FIXED_COSTS',40,DATE '2026-08-01');" >/dev/null 2>&1; then
  echo "FALHA a PK aceitou duplicata na mesma competência"
else
  echo "OK    PK rejeitou duplicata na mesma competência"
fi

psql -tA <<'SQL'
\echo '-- transações preexistentes sobreviveram e ganharam recurrence_id nulo'
SELECT CASE WHEN count(*) = 2 AND count(*) FILTER (WHERE recurrence_id IS NULL) = 2
            THEN 'OK    2 transações preservadas, recurrence_id NULL'
            ELSE 'FALHA transações: ' || count(*) END FROM transaction;

\echo '-- tabelas novas da Fase 2 existem'
SELECT CASE WHEN count(*) = 4 THEN 'OK    recurrence_rule, recurrence_occurrence, tag, transaction_tag criadas'
            ELSE 'FALHA tabelas novas encontradas: ' || count(*) END
FROM information_schema.tables
WHERE table_name IN ('recurrence_rule','recurrence_occurrence','tag','transaction_tag');

\echo '-- unicidade case-insensitive de tag por workspace'
INSERT INTO tag (workspace_id, name) VALUES ('22222222-2222-2222-2222-222222222222','Casa');
SQL

if docker exec -i "$CID" psql -q -v ON_ERROR_STOP=1 -U postgres -d migtest -c \
   "INSERT INTO tag (workspace_id,name) VALUES ('22222222-2222-2222-2222-222222222222','CASA');" >/dev/null 2>&1; then
  echo "FALHA 'CASA' passou apesar de 'Casa' já existir"
else
  echo "OK    unicidade case-insensitive de tag rejeitou 'CASA'"
fi
docker exec -i "$CID" psql -q -U postgres -d migtest -c \
   "INSERT INTO tag (workspace_id,name) VALUES ('33333333-3333-3333-3333-333333333333','Casa');" >/dev/null 2>&1 \
   && echo "OK    mesma tag permitida em outro workspace" \
   || echo "FALHA unicidade vazou entre workspaces"

echo
echo "== fim =="
