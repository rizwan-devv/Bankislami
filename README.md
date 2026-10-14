# Bank Islami

Monorepo for Bank Islami Corporate portal + Agent App APIs.

| Path | What | Port (host) |
|------|------|-------------|
| `corporate-backend/` | Corporate portal Spring Boot API | **8091** |
| `corporate-frontend/` | Corporate portal React/Vite UI | **8060** |
| `agent-api/` | Corporate Agent Android APIs | **8092** |

Repo: [github.com/rizwan-devv/Bankislami](https://github.com/rizwan-devv/Bankislami)

## Branches

| Branch | Purpose |
|--------|---------|
| `main` | Full stack (default deploy) |
| `corporate-backend` | Backend-focused work |
| `corporate-frontend` | Frontend-focused work |
| `agent-api` | Agent App API work |

## Deploy (Docker on Ubuntu)

See **[DEPLOY.md](./DEPLOY.md)** — target host `2.28.204.253`, MySQL on a **separate** server.

```bash
cp .env.example .env   # edit MYSQL_* and keys
docker compose up -d --build
```

## Local demo accounts

- Corporate admin: `admin@dfscorporate.local` / `Admin@123` (after Flyway seed)
- Agent API demo: mobile `03001234567` / MPIN `1234`
