# MK Finance

App de controle financeiro pessoal, self-host, focado no contexto brasileiro (BRL, cartão com fatura e parcelamento). Organiza a renda pelo método dos **6 pilares** (Custos Fixos, Conforto, Metas, Prazeres, Liberdade Financeira e Conhecimento) e acompanha o percentual gasto em cada um contra um alvo.

Multiusuário desde o início, com o conceito de "espaço" compartilhado. Todos os dados ficam na sua infraestrutura.

## Funcionalidades

- **Contas e transações**: receitas, despesas e transferências, com saldo por conta.
- **Categorias**: cada categoria de despesa pertence a exatamente um dos 6 pilares.
- **Cartões de crédito**: faturas, ciclo de fechamento/vencimento e compras parceladas.
- **Recorrências e calendário**: lançamentos recorrentes e visão mensal do que vence.
- **Caixinhas (vaults)**: reservas de dinheiro dentro de uma conta, com saldo próprio e aporte por pilar (Metas, Liberdade Financeira, Conhecimento).
- **Dashboard**: gastos por categoria e por pilar, evolução mensal, fatura prevista.
- **Importação**: extrato bancário em OFX e fatura de cartão em PDF (Inter), com prévia e categoria sugerida.
- **Reembolso**: abate uma despesa dividida com outras pessoas sem mexer em saldo.
- **Renda fixa**: simulador (CDI, Selic, IR, custódia), posições de investimento e patrimônio consolidado.
- **PWA**: instalável no celular, interface em pt-BR.

## Stack

| Camada | Tecnologias |
|---|---|
| API | Java 21, Spring Boot 3.5, Spring Security (sessão por cookie), Spring Data JPA, Flyway, PDFBox, springdoc-openapi |
| Web | React 19, TypeScript, Vite, Tailwind 4, Radix UI, TanStack Query, Zustand, React Router, Recharts, i18next |
| Banco | PostgreSQL 17 |
| Testes | JUnit + Testcontainers (API), Vitest + MSW (web), Playwright (e2e) |
| Infra | Docker Compose, Caddy (HTTPS), backup diário com `pg_dump` |

O contrato da API é o ponto de sincronização entre as camadas: `contracts/openapi.yaml` gera o cliente TypeScript tipado e os hooks do React Query (`orval`).

## Estrutura

```
api/        Backend Spring Boot (domain / application / infrastructure, migrations Flyway)
web/        Frontend React (features por área: accounts, cards, vaults, investments, ...)
contracts/  openapi.yaml, contrato único entre API e web
docker/     Compose de produção, Caddyfile e guia de deploy
```

## Rodando em desenvolvimento

Requisitos: JDK 21, Node 20+, Docker (para o Postgres e para os testes com Testcontainers).

**1. Banco**

```bash
docker run -d --name mkfinance-pg -p 5432:5432 \
  -e POSTGRES_DB=mkfinance -e POSTGRES_USER=mkfinance -e POSTGRES_PASSWORD=mkfinance \
  postgres:17-alpine
```

**2. API** (porta 8080, contexto `/api`; Flyway aplica as migrations no boot)

```bash
cd api
./mvnw spring-boot:run
```

Swagger UI em `http://localhost:8080/api/swagger-ui`. Variáveis `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER` e `POSTGRES_PASSWORD` sobrescrevem os padrões.

**3. Web** (o Vite faz proxy de `/api` para `localhost:8080`)

```bash
cd web
npm install
npm run dev
```

Para desenvolver sem backend, defina `VITE_API_MOCK=true` em `web/.env.development` e as requisições passam a ser atendidas por mocks MSW.

## Testes

```bash
# API
cd api && ./mvnw test

# Web: unitários
cd web && npm test

# Web: e2e (Playwright)
cd web && npm run test:e2e
```

## Contrato da API

Depois de alterar `contracts/openapi.yaml`, regenere o cliente:

```bash
cd web && npm run api:generate
```

Se o contrato mudar, o build do front quebra. É o comportamento desejado.

## Deploy

A stack de produção são 5 serviços: `caddy` (HTTPS e roteamento), `api`, `web`, `postgres` e `backup`. Só o Caddy publica portas (80/443); nada fica exposto à internet, o acesso é pela LAN ou WireGuard com DNS interno. Pensada para um LXC Debian no Proxmox.

```bash
cd docker
cp .env.example .env     # defina POSTGRES_PASSWORD
docker compose up -d
```

O passo a passo completo (build das imagens, CA interna, cópia para o LXC, backup e restore, renovação de certificado, troubleshooting) está em [`docker/README.md`](docker/README.md).
