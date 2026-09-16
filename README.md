# Curio

A daily, AI-curated brief on **AI model news** — new releases, agentic frameworks, benchmarks, pricing — with a five-question quiz at the end so the news actually sticks.

Curio reads first-party lab blogs (Anthropic, OpenAI, DeepMind, Meta AI, Mistral, Hugging Face) plus NewsAPI, asks Claude Sonnet to summarize like a senior editor, and files a personalized issue tuned to the topics you've picked. Then it grades how much you remember.

## Features

- **Personalized daily edition** — Pick from **18 model-centric topics** across four domains (Frontier Labs · Agentic & Developer Tools · Capabilities & Ecosystem · Emerging) presented as a 3-level accordion (domain → subcategory → leaf). All topics unlocked for every user.
- **AI-generated summaries** — Claude (default), Gemini, or OpenAI transforms raw articles into structured summaries with headlines, body, "why it matters", and source attribution. Provider switchable via `AI_PROVIDER`.
- **Retention quizzes** — Five multiple-choice questions per digest. Instant scoring with per-question explanations + history dashboard.
- **Email delivery** — Resend-backed daily issues at each user's chosen delivery hour in their timezone (default 08:00 UTC; the sender job runs hourly and gates per user) with open/click tracking. One-click unsubscribe via HMAC-SHA256 signed token.
- **Bring Your Own Key (BYOK)** — Users can plug in their own Claude / Gemini / OpenAI key and have digests generated against their account. Keys are AES-256-GCM encrypted at rest, never returned by any API (only a masked preview), gated behind password re-auth, and live-validated before storage.
- **Auth** — Email/password (two-step: password + emailed 6-digit code, toggleable via `AUTH_EMAIL_VERIFICATION_ENABLED`) plus single-step Google OAuth; JWT access tokens (15 min) + httpOnly SHA-256-hashed refresh cookie (3-hour sliding idle timeout, single-use rotation); password change + reset.
- **Curio Studio** — Self-serve digest workbench: signed-in users can generate today's digest and send the email on demand (`/dashboard/studio`), with live async task status instead of waiting for the scheduled jobs.
- **Newsroom (admin) dashboard** — User roster + detail, full-corpus digest browser, topic distribution, admin action audit log, **honest** job-status panel for the three scheduled jobs (digest gen / email send / cleanup) with manual triggers and per-attempt error breakdown.
- **Editorial UI** — Newsprint paper background with a Fraunces variable-serif display face, JetBrains Mono kickers, Inter Tight body, and a dark mode that flips the same DNA. Built on Vue 3 + Tailwind CSS v4.
- **Reliability** — Resilience4j circuit-breaker + bulkhead around AI calls (max 4 concurrent), 90 s time-limiter, automatic retry. ShedLock prevents double-runs across multiple backend pods.
- **404 handling** — Wrong API paths return JSON 404 (not 500); the frontend SPA has a typeset off-the-press 404 page.

## Project Structure

```
curio/
├── frontend/                       # Vue 3 + Vite SPA, editorial Tailwind v4 system
│   └── src/                        # views, components, stores (Pinia), composables,
│                                   # services (api.ts + sentry.ts), router, data/topics.ts
│                                   # (canonical 18-topic hierarchy), __tests__ (Vitest);
│                                   # tests/e2e/ holds the Cypress suites
├── backend/                        # Spring Boot 3.5 + Java 21, hexagonal architecture
│   └── src/main/java/com/curio/    # feature packages (auth, user, news, quiz, admin) each
│                                   # with controller/service/port/adapter/entity/dto/repository,
│                                   # + shared/ (scheduler, security, email, webhook, config).
│                                   # resources/db/migration = Flyway V1–V27; templates/ = email
├── docs/                           # Public long-form docs (setup, architecture, env, deploy)
├── docs-internal/                  # Private ops/product docs (gitignored — not published)
├── scripts/k6/                     # k6 load-test scenarios (auth, digests, quiz)
├── docker-compose.infra.yml        # Local dev: Postgres + Redis only
├── docker-compose.yml              # Production stack (4 services, healthchecks, limits)
├── docker-compose.tls.yml          # Caddy TLS overlay (Let's Encrypt auto-renew)
└── Caddyfile                       # Auto-TLS + security headers
```

## Technology Stack

### Frontend

| Technology | Version | Purpose |
|---|---|---|
| Vue.js 3 | 3.5 | UI framework (Composition API) |
| TypeScript | 5.9 | Type safety |
| Vite | 7 | Build tool, prod build ~250 KB gzipped total |
| Pinia | 3 | State management |
| Vue Router | 4 | Routing + auth guards |
| Tailwind CSS | 4 | Utility CSS, `@theme` token system |
| Headless UI | 1.7 | Accessible primitives (modals, switches) |
| Axios | 1.13 | HTTP client (per-call timeouts for long admin batches) |
| Fraunces / Inter Tight / JetBrains Mono | — | Editorial type system (loaded from Google Fonts) |
| Vitest | 4 | Unit testing |
| Cypress | 15 | E2E testing |

### Backend

| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Runtime |
| Spring Boot | 3.5.x | Framework |
| Spring Security + JWT (jjwt) | 0.12.6 | Stateless auth + Google OAuth2 |
| Spring Data JPA + Hibernate | — | DB access |
| PostgreSQL | 16 | Primary DB |
| Redis | 7 | AI summary cache + job-status registry |
| Flyway | Boot-managed | DB migrations (V1–V27) |
| Resilience4j | 2.2 | Circuit breaker + bulkhead + time limiter on AI calls |
| ShedLock | 5.10 | Distributed scheduler locking |
| Bucket4j | 8.10 | Rate limiting on auth endpoints |
| Springdoc OpenAPI | 2.8.6 | `/api-docs` + Swagger UI (dev only) |
| Sentry | 7.14 | Error tracking (opt-in via DSN) |
| Micrometer + Prometheus | — | Metrics (`/actuator/prometheus`) |
| Maven | 3.9+ | Build tool |

### External Services

| Service | Purpose | Required? |
|---|---|---|
| **Anthropic Claude API** | News summary + quiz generation (default provider) | Yes |
| Google Gemini API | Alternate AI provider | Optional |
| OpenAI API | Alternate AI provider | Optional |
| **NewsAPI** | News source aggregation | Yes (or digests fall back to lab blogs only) |
| **Resend** | Transactional email + webhook tracking | Yes |
| **Google OAuth** | Sign-in with Google | Optional (email/password works without it) |
| Sentry | Error tracking | Optional |

## Quick Start (local dev)

### Prerequisites

- Java 21 (`java --version`)
- Node.js 20+ (`node --version`)
- Maven 3.9+ (`mvn --version`)
- Docker + Docker Compose

### 1. Start infra

```bash
cp .env.dev.example .env.dev   # once — the infra compose fail-fasts without POSTGRES_PASSWORD
docker compose --env-file .env.dev -f docker-compose.infra.yml up -d
docker compose --env-file .env.dev -f docker-compose.infra.yml ps     # both healthy?
```

### 2. Backend

```bash
cd backend
cp .env.example .env
# Edit .env — at minimum: CLAUDE_API_KEY, RESEND_API_KEY, NEWS_API_KEY
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # dev profile; Flyway runs V1–V27 on first start
curl http://localhost:8080/actuator/health
```

### 3. Frontend

```bash
cd frontend
npm install
npm run dev                          # http://localhost:5173
```

### 4. Verify

- Open http://localhost:5173 — editorial home page (`Vol. 047`, "The AI beat, *curated* before *coffee*")
- Register an account → onboarding asks you to pick 3+ topics → archive page renders empty
- API docs: http://localhost:8080/swagger-ui.html

## Production Deployment

See [`docs/DEPLOY.md`](./docs/DEPLOY.md) for the full phased checklist (Phases 0–13, ~2–4 h end to end). The short version:

```bash
cp .env.prod.example .env.prod
# Fill in: 4 required service keys + 5 generated secrets + your domain
docker compose --env-file .env.prod pull && docker compose --env-file .env.prod up -d   # images pre-built + pushed; --build OOMs the 1 GB box
docker compose -f docker-compose.yml -f docker-compose.tls.yml --env-file .env.prod up -d
```

## API Documentation

Curio ships an interactive OpenAPI 3 spec. Swagger UI lets you call any endpoint with a bearer token, and the JSON feeds `openapi-generator` for a typed client.

- **Swagger UI**: http://localhost:8080/swagger-ui.html (disabled in prod profile)
- **OpenAPI JSON**: http://localhost:8080/api-docs
- **Health**: http://localhost:8080/actuator/health
- **Prometheus**: http://localhost:8080/actuator/prometheus (prod only)

### Endpoints at a glance

Base URL `/api/v1`. One row per feature area — see [`docs/CODEBASE.md`](./docs/CODEBASE.md) for the full endpoint reference (paths, bodies, response shapes) and the data schema.

| Area | Highlights |
|---|---|
| **Auth** | `register`, two-step `login` → `verify-code` (+`resend-code`), `google` ID-token login, `refresh`, `logout`, `forgot-password` / `reset-password` |
| **User** | `me` (profile / password / delete), `preferences` (topic beats), `unsubscribe?token=…` |
| **News & quiz** | `news/digests` (paginated archive + single), `quiz/digest/{id}`, `quiz/{id}/submit`, `quiz/history` |
| **Curio Studio** | `studio/generate`, `studio/send-email`, `studio/status` — self-serve digest workbench |
| **BYOK** | `user/api-keys` GET / POST / DELETE / `validate` — every write requires password re-auth |
| **Admin** (`ADMIN` role) | `users`, `stats` + `stats/topics` + `topics/status`, `digests`, async `generate-digests` / `send-emails`, `cleanup`, `jobs/status`, `audit-log` |

## Development

### Frontend commands

```bash
cd frontend
npm run dev           # Dev server :5173
npm run build         # Prod build → dist/  (vue-tsc -b + vite build)
npm run test:unit     # Vitest — 94 tests (as of 2026-07-18), ~2 s
npm run test:e2e      # Cypress interactive
npm run lint          # vue-tsc --noEmit
```

### Backend commands

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev         # :8080 dev profile (required — base default profile is now prod)
mvn spring-boot:run                                        # bare run = prod profile (fail-fast; real secrets required)
mvn test                                                   # 169 tests (as of 2026-07-18), ~1 min
mvn test -Dtest=ClassName                                  # single class
mvn clean package -DskipTests                              # build jar
```

### Docker

Compose modes (infra-only for local dev, full prod stack, TLS overlay) are documented in [`docs/SETUP.md`](./docs/SETUP.md) § 9; the production stack, TLS overlay, and DB access commands are in [`docs/DEPLOY.md`](./docs/DEPLOY.md).

## Environment Variables

Copy `backend/.env.example` (backend dev) or `.env.prod.example` (root, for prod) and fill it in. The minimum to boot dev — DB/Redis default to the `docker-compose.infra` values, so you only need the service keys:

| Variable | Source | Notes |
|---|---|---|
| `CLAUDE_API_KEY` | console.anthropic.com | Default AI provider |
| `RESEND_API_KEY` | resend.com | Transactional email + login codes |
| `NEWS_API_KEY` | newsapi.org | News source (digests fall back to lab blogs without it) |
| `GOOGLE_CLIENT_ID` | console.cloud.google.com | Optional — skip to disable Google login. ID-token flow: add your dev origin to **Authorized JavaScript origins** (no redirect URI). Frontend reads `VITE_GOOGLE_CLIENT_ID` at build time. |

Prod additionally needs self-generated secrets (`JWT_SECRET`, `JWT_REFRESH_SECRET`, `UNSUBSCRIBE_SECRET`, `API_KEY_ENCRYPTION_KEY`, `RESEND_WEBHOOK_SECRET`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD`) and app URLs. **[`docs/ENV_VARIABLES.md`](./docs/ENV_VARIABLES.md) documents every variable** — sources, defaults, and the prod-only fail-fast secrets.

> API calls use relative `/api/v1` paths — Vite dev proxy forwards to `:8080` in dev, nginx reverse-proxies in prod. There is no `VITE_API_URL`.

## Architecture Overview

```
                                 +----------+
                                 | NewsAPI  |
                                 +----+-----+
                                      |
+-----------+   /api/v1   +-----------v-----------+   JPA   +-------------+
| Vue SPA   +----------->| Spring Boot           +-------->| PostgreSQL  |
| (Tailwind |  HTTPS via | (hexagonal, V1-V27,    |         |  (Flyway)   |
| editorial)|  nginx     | Resilience4j, ShedLock|         +-------------+
+-----------+            +--+------+------+------+
                            |      |      |
                  +---------+      |      +-----------+
                  |                |                  |
            +-----v----+    +------v------+
            | Claude / |    |  Resend     |
            | Gemini / |    |  (email +   |
            | OpenAI   |    |   webhook)  |
            +----+-----+    +-------------+
                 |
            +----v----+
            |  Redis  |
            | (cache, |
            | jobs,   |
            | idemp.) |
            +---------+
```

- **Auth** — JWT access (15 min) + SHA-256-hashed refresh cookie (3-hour sliding idle timeout, single-use rotation), plus Google OAuth2 and the emailed-code login step.
- **Content pipeline** — `DigestGenerationJob` (06:00 UTC), hourly `EmailSendJob` (per-user delivery hour, catch-up gate, claim-before-send), `CleanupJob` (00:00 UTC), and `ExpiredAuthRowReaper` — all ShedLock-coordinated.
- **Reliability** — Resilience4j circuit breaker + bulkhead + 90 s time limiter + retry around AI calls; summaries cached in Redis (12 h TTL, keyed by `news:summaries:{topic}:{date}`).
- **Security** — token hashing, HMAC webhook + unsubscribe signatures, CSP/HSTS, session revocation on password change.
- **Hexagonal backend** — every feature package is ports + adapters (controller → `port/in`, service → `port/out`, adapter wraps Spring Data), enforced by four ArchUnit rules.

Full rationale in [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) and [`docs/architecture/ADR-001-hexagonal-architecture.md`](./docs/architecture/).

## Testing

- **Backend — 25 test classes** (as of 2026-07-18): JUnit 5 + Mockito units, `@SpringBootTest` + H2 integration (controllers, security filters, webhook signatures), and ArchUnit architecture rules.
- **Frontend — 13 Vitest files + 9 Cypress E2E specs** (as of 2026-07-18): components/stores/composables via `@vue/test-utils`; Cypress covers auth, onboarding, archive, settings, quiz, admin, reset-password, not-found, ui-screenshots.

```bash
cd backend && mvn test
cd frontend && npm run test:unit -- --run
```

## Troubleshooting

A few of the most common snags — see [`docs/SETUP.md`](./docs/SETUP.md) for the full list (Resend 403s, lab-blog 404s, mid-session 401s, and more).

- **Backend won't start — "Failed to configure a DataSource"** — confirm the infra containers are healthy (`docker compose --env-file .env.dev -f docker-compose.infra.yml ps`) and `backend/.env` has the right DB creds.
- **"Port 8080 already in use"** — `lsof -ti:8080 | xargs kill -9`.
- **"Flyway migration failed"** — usually stale dev-DB state; reset with `docker compose --env-file .env.dev -f docker-compose.infra.yml down -v` then `up -d` (wipes data).
- **Frontend won't start** — `cd frontend && rm -rf node_modules package-lock.json && npm install`.

## Further Documentation

- [`docs/README.md`](./docs/README.md) — Documentation index
- [`docs/SETUP.md`](./docs/SETUP.md) — Local development setup walkthrough
- [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) — System architecture overview
- [`docs/CODEBASE.md`](./docs/CODEBASE.md) — Architecture patterns + design decisions
- [`docs/ENV_VARIABLES.md`](./docs/ENV_VARIABLES.md) — Every environment variable explained
- [`docs/SETUP_API_KEYS.md`](./docs/SETUP_API_KEYS.md) — Per-key signup guide
- [`docs/DEPLOY.md`](./docs/DEPLOY.md) — Phased production deploy checklist
- [`docs/architecture/`](./docs/architecture/) — Hexagonal architecture decision record (ADR-001)

## Contributing

1. Branch from `main`
2. Match existing conventions (hexagonal layering on the backend, editorial design tokens on the frontend — no slate/indigo)
3. Write tests for new behavior
4. Run `mvn test && cd ../frontend && npm run test:unit -- --run && npm run build` — must all be green
5. PR with a clear "what / why / how to verify"

## License

Proprietary
