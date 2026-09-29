# Curio Documentation

Public documentation for the Curio project. For a product overview and quick start, begin with the [root README](../README.md).

| Doc | What it covers |
|---|---|
| [SETUP.md](SETUP.md) | Local development setup — prerequisites, infra containers, running backend + frontend |
| [SETUP_API_KEYS.md](SETUP_API_KEYS.md) | Obtaining the external service keys (Claude/Gemini/OpenAI, Resend, News API, Google OAuth) — signup steps and pricing; where the values go lives in ENV_VARIABLES.md / SETUP.md |
| [ENV_VARIABLES.md](ENV_VARIABLES.md) | Complete environment variable reference with defaults |
| [ARCHITECTURE.md](ARCHITECTURE.md) | System architecture — components, data flows, feature internals, security model |
| [CODEBASE.md](CODEBASE.md) | Whole-codebase map — packages, classes, routes, migrations, tests |
| [DEPLOY.md](DEPLOY.md) | Generic production deployment guide (Docker Compose, TLS, backups) |
| [architecture/](architecture/) | ADR-001: the hexagonal architecture decision record |
| [screenshots/](screenshots/) | README screenshots and the quiz GIF (captured with Cypress against stubbed APIs); `ko/` holds the Korean-edition set used by `README.ko.md` |

Component-level READMEs: [backend/README.md](../backend/README.md), [frontend/README.md](../frontend/README.md).

> Maintainer-only runbooks (environment-specific deployment, ops, planning) live in `docs-internal/`, which is intentionally not published.
