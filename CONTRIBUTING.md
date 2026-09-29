# Contributing to Curio

Thanks for taking the time to contribute. This guide covers how we work together:
branches, commits, pull requests, and what "done" means for a change.

- Local setup: [`docs/SETUP.md`](./docs/SETUP.md)
- Architecture: [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md)
- Codebase reference: [`docs/CODEBASE.md`](./docs/CODEBASE.md)
- Security issues: **do not open a public issue** — see [`SECURITY.md`](./SECURITY.md)

## Workflow

We use GitHub Flow: `main` is always releasable, and every change lands through a
short-lived branch and a pull request.

1. Open (or find) an issue for anything bigger than a typo, so the approach can be
   agreed before code is written.
2. Branch from the latest `main`.
3. Make the change, with tests.
4. Run the checks below locally.
5. Open a pull request against `main` and fill in the template.
6. Once CI is green and a maintainer approves, the PR is **squash-merged** — one PR
   becomes one commit on `main`.

## Branch names

```
<type>/<short-description>
```

Use the same `<type>` values as commit messages, kebab-case description:

- `feat/bilingual-editions`
- `fix/refresh-401-loop`
- `docs/setup-windows`

## Commit messages

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <summary>

<body — optional: what and why, not how>

<footer — optional: BREAKING CHANGE: ..., Closes #123>
```

| Type | Use for |
|---|---|
| `feat` | A new user-facing feature |
| `fix` | A bug fix |
| `docs` | Documentation only |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `test` | Adding or fixing tests |
| `perf` | Performance improvement |
| `build` | Build system, dependencies, Dockerfiles |
| `ci` | CI configuration (`.github/workflows/`) |
| `chore` | Anything else that doesn't touch `src` or tests |

Common scopes: `auth`, `user`, `news`, `quiz`, `studio`, `admin`, `email`, `i18n`,
`db`, `frontend`, `backend`.

Rules:

- Summary in the imperative mood, lower case, no trailing period, ≤ 72 characters
  (`fix(auth): return 401 for expired tokens`, not `Fixed expired tokens.`).
- Mark breaking changes with `!` (`feat(api)!: ...`) and a `BREAKING CHANGE:` footer.
- Because PRs are squash-merged, **the PR title becomes the commit message** — write
  it in the same format.

## Checks before you open a PR

CI (`.github/workflows/ci.yml`) runs the same commands; run them locally first.

```bash
# Backend
cd backend
mvn -B verify

# Frontend
cd frontend
npm ci
npm run lint                # vue-tsc -b + eslint
npm run test:unit -- --run
npm run build               # the real type-check gate
```

If your change touches a user flow, also run the Cypress suite against a running dev
server: `npx cypress run` (see `frontend/tests/e2e/`).

## Code conventions

The full list lives in [`CLAUDE.md`](./CLAUDE.md) and `docs/`. The ones reviewers
check most often:

**Backend**

- Hexagonal layout per feature package (`controller/ service/ port/in port/out
  adapter/ entity/ dto/ repository/`). ArchUnit tests enforce it — keep ports free of
  adapter imports.
- Cross-feature orchestration goes in `shared/`, never a feature service reaching
  into another feature's repository.
- API responses are records in `dto/`, never `Map<String, Object>`.
- Schema changes are a new Flyway migration (`V<n>__description.sql`); never edit a
  migration that has already shipped.
- Every email lookup/write goes through `EmailNormalizer`.

**Frontend**

- Vue 3 Composition API + TypeScript; every `services/api.ts` call is typed
  (`src/types/`).
- Every user-facing string goes through vue-i18n. Add each key to **both**
  `src/i18n/locales/en/` and `src/i18n/locales/ko/` — a parity test fails otherwise.
- Dates and numbers via `useFormat`; polling via `usePoller`.
- No emoji in the UI — use `AppIcon.vue`.

**Tests**

- New behavior comes with tests. Bug fixes come with a test that fails without
  the fix.
- Backend: JUnit 5 + Mockito (strict stubs — remove unused `when(...)`).
- Frontend: Vitest for units, Cypress for flows. Cypress specs mock sessions with
  `tests/e2e/helpers.ts` (`withAuth()`, `stubSession()`); never seed tokens in
  localStorage.

## Pull requests

- Keep PRs focused: one logical change per PR. Split refactors from behavior changes.
- Fill in the PR template: what, why, how to verify, screenshots for UI changes.
- Update docs in the same PR when behavior, endpoints, env vars, or migrations change
  (`docs/CODEBASE.md`, `docs/ENV_VARIABLES.md`, …).
- Never commit secrets. `.env*` files are gitignored except the `*.example`
  templates — add new variables there with a placeholder value.

## Reporting bugs and requesting features

Use the issue templates. For bugs, include steps to reproduce, what you expected,
what happened, and your environment. For security issues, follow
[`SECURITY.md`](./SECURITY.md) instead.

## Code of Conduct

By participating you agree to follow our [Code of Conduct](./CODE_OF_CONDUCT.md).
