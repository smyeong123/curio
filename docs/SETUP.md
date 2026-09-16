# Curio — Local Development Setup

Complete guide to running Curio locally from scratch. The fast path takes
**under 15 minutes** (excluding external API-key signup).

- For what each environment variable means, see [ENV_VARIABLES.md](./ENV_VARIABLES.md).
- For obtaining external API keys (Claude, Resend, News API, Google),
  see [SETUP_API_KEYS.md](./SETUP_API_KEYS.md).
- For production/cloud deployment, see [`DEPLOY.md`](./DEPLOY.md).

---

## 1. Prerequisites

| Tool | Version | Verify |
|------|---------|--------|
| Java | 21 | `java --version` |
| Maven | 3.9+ | `mvn --version` |
| Node.js | 20+ | `node --version` |
| npm | 10+ | `npm --version` |
| Docker | 24+ | `docker --version` |
| Docker Compose | v2+ | `docker compose version` |
| Git | 2.x | `git --version` |

---

## 2. Project Structure

```
curio/
├── frontend/                  # Vue.js 3 + Vite SPA (TypeScript)
├── backend/                   # Java 21 + Spring Boot 3.5 REST API
├── docs/                      # Documentation (you are here)
├── docker-compose.infra.yml   # PostgreSQL + Redis for local dev
├── docker-compose.yml         # Full prod/staging stack
└── docker-compose.tls.yml     # Optional Caddy TLS overlay (prod only)
```

The backend follows hexagonal architecture with 7 feature packages (`auth`,
`user`, `news`, `quiz`, `studio` (self-serve digest, `/api/v1/studio`), `admin`,
`shared`), each containing
`controller/`, `service/`, `port/`, `adapter/`, `entity/`, `dto/`, and
`repository/` layers. See [architecture/](./architecture/) for the ADR and
package design.

---

## 3. Quick Start (under 15 minutes)

```bash
# 1. Install frontend deps (Maven downloads backend deps on first run)
cd frontend && npm install && cd ..

# 2. Start infrastructure (PostgreSQL + Redis only)
#    One-time: the infra compose file fail-fasts without POSTGRES_PASSWORD and
#    auto-loads `.env` (not `.env.dev`), so seed .env.dev and pass it explicitly.
cp .env.dev.example .env.dev
docker compose --env-file .env.dev -f docker-compose.infra.yml up -d

# 3. Start the backend (runs Flyway migrations on first boot)
#    The default active profile is `prod` (fail-fast, needs real secrets), so
#    local dev MUST select the `dev` profile explicitly.
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev   # → http://localhost:8080

# 4. In a second terminal, start the frontend
cd frontend && npm run dev               # → http://localhost:5173
```

Open <http://localhost:5173> — you should see the editorial home page.

> **No API keys?** The app still boots, but AI features (digest generation,
> quizzes) and email delivery won't function. See [SETUP_API_KEYS.md](./SETUP_API_KEYS.md).

---

## 4. Environment Variables

**Local dev needs almost no configuration.** The one required step is seeding
the infra compose env file once — `cp .env.dev.example .env.dev` (the compose
file fail-fasts without `POSTGRES_PASSWORD`). Everything else has sensible dev
defaults baked in; you only set further variables to enable external
integrations.

- Frontend: optionally create `frontend/.env.local` with
  `VITE_GOOGLE_CLIENT_ID=<id>` for Google sign-in. `VITE_API_URL` is **not**
  used — the frontend calls relative `/api/v1` paths, which Vite's dev proxy
  (`vite.config.ts`) forwards to `localhost:8080`.
- Backend: copy `backend/.env.example` → `backend/.env` and fill in keys as you
  enable features.

The full, annotated list of every variable — what it is, why it exists, and how
it works — lives in **[ENV_VARIABLES.md](./ENV_VARIABLES.md)**. The minimum to
get AI features working locally is one of `CLAUDE_API_KEY` / `GEMINI_API_KEY` /
`OPENAI_API_KEY` (matching `AI_PROVIDER`), plus `NEWS_API_KEY`.

---

## 5. Infrastructure (PostgreSQL + Redis)

```bash
# Start both (PostgreSQL 16.9-alpine, Redis 7.4.2-alpine)
docker compose --env-file .env.dev -f docker-compose.infra.yml up -d

# Verify
docker exec -it curio-postgres psql -U postgres -d curio_dev -c '\dt'
docker exec -it curio-redis redis-cli PING        # → PONG
```

Defaults: database `curio_dev`, user `postgres`, password `postgres`, port
`5432`; Redis on `6379` (no password in local dev, 128MB LRU cache).

Redis caches AI-generated content (12-hour TTL, key format
`news:summaries:{topic}:{date}`). The app runs without Redis, but AI content
won't be cached.

To reset all data:

```bash
docker compose --env-file .env.dev -f docker-compose.infra.yml down -v && docker compose --env-file .env.dev -f docker-compose.infra.yml up -d
```

### Flyway migrations

Migrations run automatically on backend startup from
`backend/src/main/resources/db/migration/` (currently `V1`–`V27`, covering
users, preferences, refresh/reset tokens, digests, quizzes, quiz attempts,
the model-focused topic rewrite, BYOK user API keys with rotation tracking,
the admin audit log, performance/FTS indexes, subscription removal, the
one-digest-per-user-per-day unique index, email verification codes for
login 2FA, the one-attempt-per-(user, quiz) unique index, automatic
timezone-following for delivery, and lowercase-email normalization with a
`lower(email)` unique index — one account per mailbox regardless of case).
To inspect them, list that directory.

The application tables are: `users`, `user_preferences`, `refresh_tokens`,
`password_reset_tokens`, `email_verification_codes`, `digests`, `quizzes`,
`quiz_attempts`, `user_api_keys`, `audit_log` (plus `shedlock` for distributed
job locking and `flyway_schema_history`).

---

## 6. External API Keys

See **[SETUP_API_KEYS.md](./SETUP_API_KEYS.md)** for step-by-step signup with
cost estimates. Summary:

| Service | Purpose | Where | Free tier |
|---------|---------|-------|-----------|
| Claude API | News summarization & quiz generation | [console.anthropic.com](https://console.anthropic.com) | $5 free credits |
| Resend | Daily digest email delivery | [resend.com](https://resend.com) | 100 emails/day |
| News API | Source article fetching | [newsapi.org/register](https://newsapi.org/register) | 100 requests/day |
| Google OAuth | "Sign in with Google" | [Google Cloud Console](https://console.cloud.google.com) | Free |

Google sign-in is a browser-side ID-token flow (Google Identity Services), not a
server redirect/callback — so there is **no** redirect URI to configure. In Google
Cloud Console, add your frontend origin (`http://localhost:5173` for local dev) to
**Authorized JavaScript origins**. See [SETUP_API_KEYS.md](./SETUP_API_KEYS.md) § 4.

AI provider selection: set `AI_PROVIDER` to `claude` (default), `gemini`, or
`openai`; supply the matching `*_API_KEY`.

---

## 7. Backend

```bash
cd backend
cp .env.example .env          # then edit with your keys
mvn clean install -DskipTests
# The base application.yml defaults to the `prod` profile (`${SPRING_PROFILES_ACTIVE:prod}`),
# which fails fast without real secrets — so pass the `dev` profile for local runs.
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # runs Flyway migrations on first boot
```

Wait for `Started CurioApplication in X seconds`, then verify:

```bash
curl http://localhost:8080/actuator/health   # → {"status":"UP"}
open http://localhost:8080/swagger-ui.html    # OpenAPI UI
```

| URL | Description |
|-----|-------------|
| `http://localhost:8080/actuator/health` | Health check |
| `http://localhost:8080/swagger-ui.html` | Swagger UI |
| `http://localhost:8080/api-docs` | OpenAPI spec |
| `http://localhost:8080/api/v1/*` | REST API base |

---

## 8. Frontend

```bash
cd frontend
npm install
echo 'VITE_GOOGLE_CLIENT_ID=<your-google-client-id>' > .env.local   # optional
npm run dev                   # Vite proxy forwards /api → localhost:8080
```

Open <http://localhost:5173>. Key routes: `/login`, `/register`,
`/dashboard` (requires auth).

---

## 9. Docker Compose Modes

| File | Purpose | Command |
|------|---------|---------|
| `docker-compose.infra.yml` | Local dev: Postgres + Redis only (run app natively for HMR) | `docker compose --env-file .env.dev -f docker-compose.infra.yml up -d` |
| `docker-compose.yml` | Full prod/staging stack (backend, frontend, Postgres, Redis) | `docker compose --env-file .env.prod up -d` |
| `docker-compose.tls.yml` | Optional Caddy + Let's Encrypt TLS overlay (prod, needs a domain) | `docker compose --env-file .env.prod -f docker-compose.yml -f docker-compose.tls.yml up -d` |

In production only the frontend port is exposed; nginx reverse-proxies `/api/`
to the backend internally — no CORS configuration needed.

---

## 10. Running Tests

```bash
# Backend — JUnit 5 + Mockito, @SpringBootTest with H2, MockMvc, ArchUnit
cd backend
mvn test
mvn test -Dtest=AuthServiceTest     # single class
mvn test jacoco:report              # with coverage

# Frontend — Vitest unit + Cypress E2E (E2E needs backend on :8080)
cd frontend
npm run test:unit
npm run test:e2e
npm run lint
```

---

## 11. Daily Workflow

```bash
# Terminal 1: infra (if not running)
docker compose --env-file .env.dev -f docker-compose.infra.yml up -d
# Terminal 2: backend (dev profile — unset defaults to fail-fast prod)
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
# Terminal 3: frontend
cd frontend && npm run dev
```

Create a test user:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"Test123!@#","firstName":"Test","lastName":"User"}'
```

Or register via the UI at `http://localhost:5173/register` and complete
onboarding (select 3+ topics).

---

## 12. Troubleshooting

| Symptom | Fix |
|---------|-----|
| "Failed to configure a DataSource" | Postgres not running: `docker compose --env-file .env.dev -f docker-compose.infra.yml up -d`; check `backend/.env` credentials. |
| "Port 8080 already in use" | `lsof -ti:8080 \| xargs kill -9` |
| "Port 5173 already in use" | `lsof -ti:5173 \| xargs kill -9` |
| "Flyway migration failed" | Reset DB: `docker compose --env-file .env.dev -f docker-compose.infra.yml down -v && docker compose --env-file .env.dev -f docker-compose.infra.yml up -d` |
| "Cannot find module" (frontend) | `cd frontend && rm -rf node_modules package-lock.json && npm install` |
| CORS errors | Dev proxy handles routing; verify backend on :8080. If bypassing the proxy, set `FRONTEND_URL=http://localhost:5173`. |
| 401 Unauthorized | Access token expired (60 min dev / 15 min prod); refresh page to auto-refresh, or re-login if the refresh token expired (3-hour sliding idle timeout, `JWT_REFRESH_EXPIRATION`). |
| Redis connection refused | `docker compose --env-file .env.dev -f docker-compose.infra.yml up -d` (app works without it; content just isn't cached). |
| Google sign-in prompt doesn't appear | Curio uses Google Identity Services (an ID-token flow, no redirect). Add your frontend origin (`http://localhost:5173`) to **Authorized JavaScript origins** in Google Cloud Console, and confirm `VITE_GOOGLE_CLIENT_ID` is set. |

---

## Further Reading

- [ENV_VARIABLES.md](./ENV_VARIABLES.md) — every environment variable explained
- [SETUP_API_KEYS.md](./SETUP_API_KEYS.md) — API key signup with cost estimates
- [CODEBASE.md](./CODEBASE.md) — component breakdown and design decisions
- [ARCHITECTURE.md](./ARCHITECTURE.md) — full architecture reference and data flows
- [architecture/](./architecture/) — hexagonal architecture decision record (ADR-001)
