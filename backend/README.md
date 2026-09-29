# Curio Backend

Spring Boot 3.5 / Java 21 REST API for Curio — fetches news, has an AI provider write the digest and quiz, and emails it on each reader's schedule. Base path: `/api/v1`.

## Run it

Needs Java 21, Maven 3.9+, and Postgres + Redis from the infra compose:

```bash
cp ../.env.dev.example ../.env.dev      # once
docker compose --env-file ../.env.dev -f ../docker-compose.infra.yml up -d

cp .env.example .env                    # add CLAUDE_API_KEY, RESEND_API_KEY, NEWS_API_KEY
mvn spring-boot:run -Dspring-boot.run.profiles=dev
curl http://localhost:8080/actuator/health
```

> **Always pass the `dev` profile.** `application.yml` defaults to `prod`, which fails fast without real secrets.

In dev, Swagger UI is at <http://localhost:8080/swagger-ui.html>. Every variable is documented in [`docs/ENV_VARIABLES.md`](../docs/ENV_VARIABLES.md).

## Commands

| Command | What it does |
|---|---|
| `mvn spring-boot:run -Dspring-boot.run.profiles=dev` | Run locally (Flyway migrates on start) |
| `mvn test` | All tests (H2, `test` profile) |
| `mvn test -Dtest=ClassName` | One test class |
| `mvn clean package -DskipTests` | Build the jar |

## Where things live

Hexagonal (ports & adapters), one package per feature: `auth`, `user`, `news`, `quiz`, `studio`, `admin`, plus `shared` for cross-feature orchestration (digest pipeline, batches, email, webhooks, scheduler, security, config).

```
com/curio/<feature>/
├── controller/          REST endpoints
├── port/in/             Use-case interfaces
├── service/             Business logic
├── port/out/            Outbound ports (e.g. AiService)
├── adapter/persistence/ JPA adapters
└── entity/  dto/  repository/
```

ArchUnit tests enforce these boundaries — see [ADR-001](../docs/architecture/ADR-001-hexagonal-architecture.md).

## Conventions

- **Cross-provider AI fixes go in `AbstractAiProvider`**, not in each of Claude/Gemini/OpenAI.
- **Emails** are always read and written through `EmailNormalizer`.
- **API responses are records** in `dto/`, never `Map<String, Object>`.
- **Concurrency** uses atomic conditional UPDATEs (claim-before-act), not check-then-act.
- **Day boundaries** are UTC (`LocalDate.now(ZoneOffset.UTC)`).

## Reference

- Endpoints, DB schema, migrations, scheduled jobs, profiles → [`docs/CODEBASE.md` § Backend](../docs/CODEBASE.md#backend)
- Data flows, AI pipeline, security model → [`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md)
- Docker image and production stack → [`docs/DEPLOY.md`](../docs/DEPLOY.md)
