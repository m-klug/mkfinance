# Deploy do MK Finance (Docker + Proxmox LXC)

Guia autocontido para subir a stack de produção. Cobre build das imagens, CA
interna, cópia para o LXC e acesso. Fonte da verdade da stack: o
`docker-compose.yml` deste diretório.

A stack são 5 serviços: **caddy** (borda HTTPS + roteamento), **api** (Spring
Boot / Java 21), **web** (SPA estática servida por Caddy), **postgres** e
**backup** (pg_dump diário). Só o Caddy publica portas (80/443); o resto fica na
rede interna. Nada é exposto à internet — acesso pela LAN / WireGuard + DNS
interno.

---

## 1. Requisitos do LXC (base de produção — só rodar)

| Recurso | Mínimo | Recomendado |
|---|---|---|
| vCPU | 1 | 2 |
| RAM | 1 GB (com `-Xmx384m` na API) | 2 GB |
| Disco | 8 GB | 16 GB |
| Swap | 512 MB | 1 GB |

- **SO**: template Debian 12.
- **Timezone**: `TZ=America/Sao_Paulo` (datas de negócio e agendamento do backup).
- **Docker dentro de LXC (Proxmox)**: habilite `features: nesting=1,keyctl=1` no
  container. Unprivileged costuma bastar no PVE 8; se houver problema de storage
  driver, use privilegiado ou rode Docker numa VM.

Em runtime a stack consome ~1 GB de RAM (a JVM da API é o maior peso). Os 2 GB
recomendados são folga para GC e cache do Postgres. Para caber em 1 GB, limite o
heap no serviço `api` do compose:

```yaml
    environment:
      JAVA_TOOL_OPTIONS: "-Xmx384m"   # ou -XX:MaxRAMPercentage=50
```

---

## 2. Build das imagens (no PC de dev)

Os Dockerfiles são multi-stage e compilam do fonte (Maven para a API, Vite para o
web). A base de produção **não** builda — você builda no PC e carrega as imagens
prontas no LXC.

> ⚠️ **Arquitetura**: se o PC é Apple Silicon (arm64) e o LXC é amd64, é preciso
> cross-buildar para amd64, senão dá o erro `platform (linux/arm64) does not match
> host platform (linux/amd64)`.

```bash
# no PC, dentro de mk-finance/docker/
DOCKER_DEFAULT_PLATFORM=linux/amd64 docker compose build

# confira que ficou amd64
docker image inspect mk-finance-api --format '{{.Architecture}}'   # amd64
docker image inspect mk-finance-web --format '{{.Architecture}}'   # amd64

# exporte as duas imagens
docker save mk-finance-api mk-finance-web | gzip > mk-images.tar.gz
```

As imagens base (`caddy`, `postgres`, `postgres-backup-local`) são multi-arch e o
LXC puxa a variante amd64 sozinho no primeiro `up` (precisa de internet nesse
momento; se o LXC for isolado, pré-carregue-as também).

---

## 3. Certificado (CA interna)

O Caddy serve um certificado folha assinado por uma CA que **você** gera uma vez.
Truste a raiz uma vez por dispositivo e o mesmo cert vale no PC e no LXC (o SAN
cobre `mkfinance.internal` e o IP do LXC). Detalhes no `certs/gen-ca.sh`.

```bash
# no PC, dentro de mk-finance/docker/certs/
./gen-ca.sh
```

Gera:
- `mkfinance-ca.key` / `mkfinance-ca.crt` — a **CA**. A `.key` **nunca sai do PC**.
- `mkfinance.key` / `mkfinance.crt` — a **folha**, servida pelo Caddy.

**Trustar a raiz** (`mkfinance-ca.crt`), uma vez por dispositivo que for acessar:

```bash
# macOS
sudo security add-trusted-cert -d -r trustRoot \
  -k /Library/Keychains/System.keychain mkfinance-ca.crt

# Debian (host/LXC)
sudo cp mkfinance-ca.crt /usr/local/share/ca-certificates/ && sudo update-ca-certificates
```

> Firefox usa store próprio — importe a raiz em Configurações → Certificados.

---

## 4. Arquivos esperados no LXC

Na pasta `docker/` do LXC (todos os caminhos do compose são relativos a ela):

| Arquivo | Obrigatório | Observação |
|---|---|---|
| `docker-compose.yml` | ✅ | orquestra os serviços |
| `.env` | ✅ | `POSTGRES_PASSWORD=<senha-forte>` (não o `change-me` do exemplo) |
| `Caddyfile` | ✅ | montado pelo Caddy; sem ele o container não sobe |
| `certs/mkfinance.crt` + `certs/mkfinance.key` | ✅ | **só a folha** — copie do PC |
| `backups/` | — | o Docker cria no primeiro `up` |

**Nunca copie para o LXC**: `mkfinance-ca.key` (chave da CA) nem o código-fonte
`api/`/`web/` (as imagens já vêm prontas).

Copiando a folha do PC para o LXC (canal seguro):

```bash
# via SSH
scp certs/mkfinance.crt certs/mkfinance.key root@192.168.1.247:/caminho/mk-finance/docker/certs/
# ou, do host Proxmox
pct push <vmid> certs/mkfinance.crt /caminho/mk-finance/docker/certs/mkfinance.crt
pct push <vmid> certs/mkfinance.key /caminho/mk-finance/docker/certs/mkfinance.key
```

---

## 5. Subir a stack (no LXC)

```bash
# carregue as imagens exportadas no passo 2
gunzip -c mk-images.tar.gz | docker load

cd /caminho/mk-finance/docker
chmod 600 certs/mkfinance.key
docker compose config -q          # valida o compose
docker compose up -d

docker compose ps                 # caddy deve estar Up com 0.0.0.0:443->443
docker compose logs -f api        # acompanha o boot do Spring + Flyway
```

---

## 6. Acessar do cliente (PC/celular)

1. **Trustar a CA** no dispositivo (passo 3), se ainda não fez.
2. **Resolver o nome** — o ideal é o **DNS interno** apontar `mkfinance.internal`
   → IP do LXC (funciona em todos os dispositivos sem ajuste). Sem DNS interno,
   no macOS/Linux edite `/etc/hosts`:
   ```bash
   echo "192.168.1.247  mkfinance.internal" | sudo tee -a /etc/hosts
   sudo dscacheutil -flushcache && sudo killall -HUP mDNSResponder   # macOS
   ```
3. Abra **`https://mkfinance.internal`** (ou `https://<ip-do-lxc>`).
4. Tela de login → **registre** o primeiro usuário (cria o workspace e semeia
   categorias/pilares).

> ⚠️ Escolha **uma** origem (nome OU ip) e use sempre — são origens distintas para
> o navegador; sessão, cookies e o PWA não passam de uma para a outra.

**Instalar como app (PWA)**: com a CA confiável e HTTPS válido, Chrome/Edge
mostram o ícone de instalar; no Safari, Compartilhar → "Adicionar à Dock".

---

## 7. Backup e restore

O serviço `backup` roda `pg_dump` diário em `./backups/`, com retenção
(14 dias / 8 semanas / 12 meses). Leve a pasta `backups/` para fora do HDD
principal do Proxmox periodicamente (o `vzdump` do LXC também cobre isso).

Restore de um dump num banco limpo:

```bash
gunzip -c backups/daily/<arquivo>.sql.gz | \
  docker compose exec -T postgres psql -U mkfinance -d mkfinance
```

---

## 8. Renovação do certificado folha

A folha expira em ~825 dias. Para renovar, no PC:

```bash
cd mk-finance/docker/certs && ./gen-ca.sh    # reaproveita a CA, gera folha nova
```

Recopie `mkfinance.crt`/`mkfinance.key` para o LXC (passo 4) e
`docker compose restart caddy`. A CA raiz e a confiança nos dispositivos
continuam valendo — não precisa re-trustar nada.

---

## 9. Troubleshooting

| Sintoma | Causa provável | Ação |
|---|---|---|
| "Safari can't find the server" | nome não resolve ou host inalcançável | teste pelo IP; `ping <ip>`; `nc -vz <ip> 443`; `/etc/hosts` + flushcache |
| Aviso de certificado | CA não confiável no dispositivo | trustar `mkfinance-ca.crt` (passo 3); reabrir o navegador |
| `platform (arm64) does not match (amd64)` | imagem buildada em Apple Silicon | rebuild com `DOCKER_DEFAULT_PLATFORM=linux/amd64` (passo 2) |
| Caddy não sobe | falta `Caddyfile` ou a folha em `certs/` | conferir arquivos (passo 4); `docker compose logs caddy` |
| `compose up` tenta buildar | imagens não carregadas | `docker load` das imagens (passo 5) |
| 401/403 no login | fluxo CSRF | acessar por origem única; cookie `XSRF-TOKEN` (path `/`) ecoado em `X-XSRF-TOKEN` |

---

## Quando houver um domínio real

Trocar a CA interna por Let's Encrypt via DNS-01 (sem expor portas) — ver o
comentário no topo do `Caddyfile` e `PLANEJAMENTO.md` §3.4.
