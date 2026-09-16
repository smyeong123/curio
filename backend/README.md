# Curio Backend

AI-powered personalized news service backend — Spring Boot 3.5 / Java 21. Generates personalized news digests with Claude AI, builds comprehension quizzes, and delivers them by email.

This README covers **what the module is and how to run/test it**. For system-wide reference, see the central docs:

- Environment variables (complete): [`../docs/ENV_VARIABLES.md`](../docs/ENV_VARIABLES.md)
- Full endpoint reference + database schema: [`../docs/CODEBASE.md`](../docs/CODEBASE.md)
- Architecture (hexagonal, ports & adapters): [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md)
- End-to-end setup walkthrough: [`../docs/SETUP.md`](../docs/SETUP.md)

## Tech Stack

- **Framework:** Spring Boot 3.5, Java 21
- **Database:** PostgreSQL 16 with Flyway migrations
- **Cache:** Redis
- **Auth:** JWT (JJWT 0.12.6) + Google OAuth2, email-based 2FA on password login
- **AI:** Provider-agnostic (`AiService`) — Claude (default), Gemini, OpenAI
- **Email:** Resend + Thymeleaf templates
- **News:** NewsAPI.org
- **Build:** Maven 3.9+
- **Docs:** OpenAPI / Swagger UI (dev only)

## Package Layout (hexagonal)

Ports & adapters, organized by feature. Each feature package (`auth`, `user`, `news`, `quiz`, `admin`, `studio`) follows the same shape:

```
src/main/java/com/curio/
├── <feature>/
│   ├── controller/          # REST endpoints
│   ├── service/             # Business logic — implements inbound ports (UseCase)
│   ├── port/in/             # Inbound ports (use-case interfaces)
│   ├── port/out/            # Outbound ports (dependency abstractions)
│   ├── adapter/persistence/ # JPA adapters implementing outbound ports
│   ├── entity/              # JPA entities
│   ├── dto/                 # Request/response DTOs
│   └── repository/          # Spring Data JPA repositories
└── shared/                  # scheduler/, security/, email/, webhook/, config/,
                             # concurrent/ (SingleFlight), util/ (EmailNormalizer)
```

Controllers inject inbound port interfaces; services depend on outbound ports, not concrete repositories. `@Component` JPA adapters wrap Spring Data repositories. ArchUnit tests enforce the boundaries (ports isolated, no adapter-to-adapter deps). See [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md) for the full picture.

## Prerequisites

- Java 21, Maven 3.9+
- PostgreSQL 16, Redis — run both via the infra compose (seed `.env.dev` once, it fail-fasts without `POSTGRES_PASSWORD`):
  ```bash
  cp ../.env.dev.example ../.env.dev   # once
  docker compose --env-file ../.env.dev -f ../docker-compose.infra.yml up -d
  ```

## Getting Started

### 1. Configure environment

```bash
cp .env.example .env
# Edit .env with your credentials
```

The dev-quickstart variables (from `.env.example`) — DB and Redis default to localhost, so you only need API keys:

| Variable | Description |
|---|---|
| `GOOGLE_CLIENT_ID` | Google OAuth client id (ID-token flow — no secret needed) |
| `AI_PROVIDER` | `claude` (default), `gemini`, or `openai` |
| `CLAUDE_API_KEY` | Anthropic Claude API key (for the default provider) |
| `GEMINI_API_KEY`, `OPENAI_API_KEY` | Alternate provider keys (optional) |
| `RESEND_API_KEY` | Resend email service key |
| `RESEND_WEBHOOK_SECRET` | Resend webhook signature secret |
| `NEWS_API_KEY` | NewsAPI.org key |

See [`../docs/ENV_VARIABLES.md`](../docs/ENV_VARIABLES.md) for every variable (JWT secrets, BYOK encryption key, unsubscribe secret, login-2FA tuning, timezone fallback, prod-only secrets, and generation commands).

### 2. Run the application

The infra compose creates the `curio_dev` database automatically — no manual `CREATE DATABASE` needed.

```bash
mvn clean install -DskipTests          # build (skip tests for speed)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

> **Dev profile gotcha:** the base `application.yml` defaults to `${SPRING_PROFILES_ACTIVE:prod}`, so a bare `mvn spring-boot:run` boots the **fail-fast prod profile** (real secrets required). For local dev you MUST select the dev profile explicitly: `-Dspring-boot.run.profiles=dev` (or `SPRING_PROFILES_ACTIVE=dev`).

The server starts at `http://localhost:8080`. Swagger UI is at `/swagger-ui.html` in dev (disabled in prod). Health check: `curl http://localhost:8080/actuator/health`.

### Profiles

| Profile | Database | Usage |
|---|---|---|
| `dev` | PostgreSQL (localhost) | Local development |
| `prod` | PostgreSQL (env vars) | Production (fail-fast secret validation) |
| `test` | H2 in-memory | Automated testing |

## Testing

```bash
mvn test                                # all tests (H2 in-memory, test profile)
mvn test -Dtest=AuthControllerIntegrationTest   # a single class
mvn clean install -DskipTests           # build, skipping tests
```

28 test classes (as of 2026-09-16): Mockito unit tests for services, `@SpringBootTest` integration tests, MockMvc controller tests, security tests, and ArchUnit architecture rules.

## API Surface

Base URL: `/api/v1`. One line per feature area — see [`../docs/CODEBASE.md`](../docs/CODEBASE.md) for the full endpoint reference with request/response shapes.

| Area | Base path | What it does |
|---|---|---|
| Auth | `/auth` | Register, two-step email/password login (`/login` → `/verify-code`, with `/resend-code`), Google OAuth (`/google`), token refresh/logout, forgot/reset password |
| User | `/user` | Profile, topic preferences, delivery hour/timezone, two-step `/unsubscribe`, BYOK keys under `/user/api-keys` |
| News | `/news` | Paginated digest retrieval + full-text `/search` |
| Quiz | `/quiz` | Quiz retrieval, submission (server-side scoring, "better score wins"), history |
| Studio | `/studio` | Self-serve digest workbench — `/generate`, `/send-email`, `/status` (authenticated) |
| Admin | `/admin` | User management, stats, digest generation, email ops, cleanup, audit log (ADMIN role) |
| Webhook | `/webhooks/email` | `POST` handler for Resend email tracking events (HMAC-SHA256 signature verified) |

## Database Schema

Managed via Flyway migrations in `src/main/resources/db/migration/` (V1–V27). Tables — see [`../docs/CODEBASE.md`](../docs/CODEBASE.md) for columns and index details:

`users`, `user_preferences`, `refresh_tokens`, `password_reset_tokens`, `email_verification_codes`, `digests`, `quizzes`, `quiz_attempts`, `shedlock`, `user_api_keys`, `audit_log`.

The `audit_log` table (V18) records admin actions with columns: `actor_id`, `actor_email`, `action`, `target_type`, `target_id`, `request_id`, `metadata` (JSONB), `created_at`.

## AI Provider Abstraction

`AiService` is provider-agnostic. `ClaudeService` (default), `GeminiService`, and `OpenAiService` all extend `AbstractAiProvider`, which owns the shared orchestration (Redis caching, single-flight cold-cache dedupe, retry/backoff, circuit-breaker/bulkhead wrapper, JSON fence-stripping, and the shared summary/quiz prompts). Subclasses only implement the provider wire format, so cross-provider fixes live in the base class. Select the provider via `AI_PROVIDER`. Details in [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).

## Scheduled Jobs

Four cron jobs in `shared/scheduler/`, distributed-locked via ShedLock:

1. **DigestGenerationJob** — 6 AM UTC; pre-generates digests, plus generate-if-missing at send time
2. **EmailSendJob** — hourly; emails each user whose local hour is at or past their delivery hour (catch-up gate; `claimForEmailSend` prevents double-sends)
3. **CleanupJob** — midnight UTC; deletes digests/quizzes older than 30 days in bounded batches
4. **ExpiredAuthRowReaper** — reaps expired refresh/reset tokens and login 2FA codes

Admins can trigger digest generation, email send, and cleanup manually (generate/send run asynchronously). See [`../docs/CODEBASE.md`](../docs/CODEBASE.md).

## Docker

```bash
docker build -t curio-backend .              # multi-stage: Maven build → Alpine JRE runtime
docker run -p 8080:8080 --env-file .env curio-backend
```

For the full containerized stack (backend + frontend + PostgreSQL + Redis), use the root `docker-compose.yml`. See [`../docs/DEPLOY.md`](../docs/DEPLOY.md). The image runs as a non-root user with a `/actuator/health` container health check.
