# Deploy Bank Islami on Ubuntu (Docker)

**App server:** `2.28.204.253` (this host — empty Docker, 15 GB RAM)  
**Database:** MySQL on **another** server (set `MYSQL_HOST` in `.env`)  
**Git:** https://github.com/rizwan-devv/Bankislami

## One-time server prep

```bash
# as root on 2.28.204.253
apt update && apt install -y git docker.io docker-compose-v2
systemctl enable --now docker

# MySQL on remote DB host must allow this server's IP:
#   GRANT ALL ON bankislami_corporate.* TO 'dfs'@'%';  -- or specific IP
#   Firewall: allow TCP 3306 from 2.28.204.253
```

## Clone & configure

```bash
cd /opt
git clone https://github.com/rizwan-devv/Bankislami.git
cd Bankislami
git checkout main

cp .env.example .env
nano .env
```

Set at least:

```env
MYSQL_HOST=<other-mysql-ip>          # e.g. 188.245.241.152
MYSQL_PASSWORD=<real-password>
CORPORATE_PORTAL_API_KEY=<portal-key>
FRONTEND_BASE_URL=http://2.28.204.253:8060
VITE_API_BASE=http://2.28.204.253:8091
AGENT_PUBLIC_BASE_URL=http://2.28.204.253:8092
```

## Build & run containers

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f --tail=100
```

| Container | Host port | URL |
|-----------|-----------|-----|
| `bankislami-frontend` | 8060 | http://2.28.204.253:8060 |
| `bankislami-backend` | 8091 | http://2.28.204.253:8091 |
| `bankislami-agent-api` | 8092 | http://2.28.204.253:8092/health |

Open firewall / security group for **8060, 8091, 8092** (and SSH).

## Agent Android app

Point all DFS base URLs to the agent API:

```
http://2.28.204.253:8092/
```

## Update after code push

```bash
cd /opt/Bankislami
git pull origin main
docker compose up -d --build
```

## Notes

- No MySQL container on this host — DB is external.
- Do **not** commit `.env`.
- First backend start runs Flyway on `bankislami_corporate`.
- Live agent transactions: set `DFS_PROXY_ENABLED=true` when ready.
