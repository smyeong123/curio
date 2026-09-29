# Curio Frontend

Vue 3 + TypeScript SPA for Curio — the reader app (archive, quiz, Studio, settings) and the admin Newsroom.

## Run it

Needs Node.js 20+ and the backend on `http://localhost:8080` (see [`../backend/README.md`](../backend/README.md)).

```bash
npm install
npm run dev          # http://localhost:5173
```

Optional `.env.local`:

```bash
VITE_GOOGLE_CLIENT_ID=<google-oauth-client-id>   # enables "Sign in with Google"
VITE_SENTRY_DSN=<sentry-dsn>                     # error tracking
```

There is no `VITE_API_URL`: the app calls relative `/api/v1` paths, proxied by Vite in dev and nginx in production.

## Scripts

| Command | What it does |
|---|---|
| `npm run dev` | Dev server with HMR |
| `npm run build` | Type-check + production build → `dist/` (the real verification gate) |
| `npm run lint` | `vue-tsc -b` + ESLint |
| `npm run test:unit` | Vitest unit tests |
| `npm run test:e2e` | Cypress (interactive); `npx cypress run` for headless. Needs the dev server; APIs are stubbed |

## Where things live

```
src/
├── views/        Pages by feature (auth/, dashboard/, admin/, legal/)
├── components/   ui/ base set + feature folders
├── stores/       Pinia: auth, user, news, quiz
├── composables/  useFormat, usePoller, useLocale, …
├── services/     api.ts — typed Axios client with JWT refresh
├── i18n/         locales/{en,ko}/<namespace>.json
├── types/        One file per API area
└── router/       Routes + auth/admin guards
```

## Conventions

- **Every user-facing string goes through i18n.** Add each key to both `locales/en` and `locales/ko` — a parity test fails otherwise.
- **Dates and numbers** go through `useFormat`; **polling** through `usePoller`.
- **No emoji** in the UI — use `AppIcon.vue`.
- **E2E sessions** are mocked with `tests/e2e/helpers.ts` (`withAuth()` + `stubSession()`); never seed tokens in localStorage.

## Reference

- Routes, stores, components, API client map, tests → [`docs/CODEBASE.md` § Frontend](../docs/CODEBASE.md#frontend)
- Auth flow and session model → [`docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md#feature-internals)
- Docker image and deployment → [`docs/DEPLOY.md`](../docs/DEPLOY.md)
