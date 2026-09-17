# Curio Frontend

AI-powered personalized news service frontend — Vue 3 + TypeScript + Vite + Pinia. Delivers daily news digests tailored to user interests, with comprehension quizzes.

This README covers **what the module is and how to run/test it**. For system-wide reference, see the central docs:

- Full endpoint reference + database schema: [`../docs/CODEBASE.md`](../docs/CODEBASE.md)
- Architecture: [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md)
- Environment variables (complete): [`../docs/ENV_VARIABLES.md`](../docs/ENV_VARIABLES.md)
- End-to-end setup walkthrough: [`../docs/SETUP.md`](../docs/SETUP.md)

## Tech Stack

- **Framework:** Vue 3 (Composition API)
- **Build:** Vite
- **State:** Pinia
- **Routing:** Vue Router 4
- **HTTP:** Axios (with JWT interceptors + silent refresh)
- **Styling:** Tailwind CSS v4 (editorial `@theme` token system)
- **Language:** TypeScript
- **Testing:** Vitest (unit), Cypress (E2E)

## Prerequisites

- Node.js 20+, npm
- Backend running at `http://localhost:8080` (see `../backend/README.md`)

## Getting Started

### 1. Install and configure

```bash
npm install
```

Create `.env.local` in the frontend root:

```bash
VITE_GOOGLE_CLIENT_ID=<your-google-oauth-client-id>
VITE_SENTRY_DSN=<optional-sentry-url>
```

Only `VITE_GOOGLE_CLIENT_ID` is required; `VITE_SENTRY_DSN` is optional.

> **No `VITE_API_URL`.** The app calls `/api/v1` via **relative paths**. In local dev, Vite's dev proxy (`vite.config.ts`) forwards `/api` to `localhost:8080`. In production, nginx reverse-proxies `/api/` to the backend container. See [`../docs/ENV_VARIABLES.md`](../docs/ENV_VARIABLES.md).

### 2. Run the dev server

```bash
npm run dev          # http://localhost:5173, with HMR
```

## Scripts

```bash
npm run dev          # Vite dev server
npm run build        # production build → dist/
npm run preview      # preview the production build locally
npm run test:unit    # Vitest unit tests
npm run test:e2e     # Cypress E2E tests
npm run lint         # TypeScript type checking
```

## Project Structure

```
src/
├── views/          # Page components (auth/, legal/, dashboard/, admin/)
├── components/     # ErrorBoundary.vue + auth/, layout/, quiz/, settings/, ui/
├── stores/         # Pinia stores (below)
├── services/       # api.ts — Axios client with JWT interceptors
├── router/         # index.ts — routes + auth guards
├── i18n/           # vue-i18n instance + locales/{en,ko}/<namespace>.json catalogs
├── types/          # TypeScript types matching backend DTOs
├── composables/    # Reusable composables
├── utils/          # Helpers (e.g. safeUrl.ts)
├── App.vue
└── main.ts
```

**Components (complete list):**

- `ErrorBoundary.vue`
- `auth/`: `LoginForm.vue`, `RegisterForm.vue`
- `layout/`: `DashboardLayout.vue`, `LegalLayout.vue`
- `quiz/`: `QuizQuestion.vue`, `QuizResults.vue`
- `settings/`: `ApiKeyManager.vue` (BYOK key management)
- `ui/`: `AppIcon.vue`, `BaseButton.vue`, `BaseInput.vue`, `BaseModal.vue`, `BaseSpinner.vue`, `ToastContainer.vue`, `LanguageToggle.vue` (English / 한국어 edition switch)

## State Management (Pinia)

| Store | Responsibility |
|---|---|
| `auth.ts` | JWT access token (in-memory only), login/logout, silent refresh, `isAdmin` |
| `user.ts` | User profile and topic preferences |
| `news.ts` | Digest archive and pagination |
| `quiz.ts` | Active quiz and history |
| `persistence.ts` | Cross-store persistence plumbing (registered in `main.ts`) |

The access token lives in memory only; the refresh token is an httpOnly `SameSite=Strict` cookie scoped to `/api/v1/auth`, sent automatically on `/auth/refresh`. Axios auto-refreshes on 401.

## Routes

Defined in `router/index.ts`:

- Public: `/`, `/login`, `/register`, `/verify` (login 2FA code step), `/reset-password`, `/privacy`, `/terms`, `/contact`
- Authenticated (`requiresAuth`): `/onboarding`, and under `/dashboard`: `archive`, `quiz/:digestId`, `quiz-history`, `studio`, `settings`
- Admin (`requiresAdmin`): `/admin/dashboard`, `/admin/digests`, `/admin/users`, `/admin/users/:id`, `/admin/stats`, `/admin/audit` (`/admin` redirects to the dashboard)
- `/:pathMatch(.*)*` → 404 (`NotFoundView`)

Guards: `requiresAuth` redirects to `/login`; `requiresAdmin` re-syncs the role from the server on entry, then redirects non-admins to the archive.

## Authentication Flow

Email/password sign-in is **two-step (email-based 2FA)**:

1. `POST /auth/login` — server validates the password, emails a 6-digit code, and returns a `VERIFICATION_REQUIRED` challenge (kept in the auth store's `pendingVerification`, routed to `/verify`).
2. On `/verify`, `POST /auth/verify-code` with the code (5 attempts; lockout offers a password reset). `POST /auth/resend-code` emails a fresh code.

**Google login** uses Google Identity Services: the ID token is sent to `POST /auth/google` and verified server-side against Google's tokeninfo (audience + issuer) — there is no redirect-URI callback. It is single-step and skips the code.

For the API contract, see [`../docs/CODEBASE.md`](../docs/CODEBASE.md).

## Testing

**Unit (Vitest)** — `npm run test:unit`. 42 test files / 214 tests in `src/__tests__/` (as of 2026-09-17) covering stores (auth, user, news, quiz), the extracted components (mastheads, pager, segmented control, archive/studio/admin pieces), composables (`useToast`, `useTopicLabels`, `useFormat`, `usePoller`, `usePagedAdminList`, `useTopicSelection`, `useDisclosureSet`), the i18n layer (`useLocale`, catalog key-tree parity), views in both editions, and `safeUrl`. Runs in jsdom; `setup.ts` installs the app i18n singleton, resets it to English and stubs `matchMedia` before each test.

**E2E (Cypress)** — `npm run test:e2e`. 10 specs in `tests/e2e/` (as of 2026-09-16): `auth`, `onboarding`, `quiz`, `archive`, `settings`, `admin`, `reset-password`, `not-found`, `i18n`, `ui-screenshots`. The support file (`tests/e2e/support.ts`) pins the UI edition to English before boot unless a test already chose one. Session mocking is centralized in `tests/e2e/helpers.ts`: the app keeps the access token in memory and re-establishes sessions via silent `POST /auth/refresh`, so specs seed the localStorage `user` (`withAuth()`) and stub the refresh call (`stubSession()`) — **never seed localStorage tokens**.

## Docker

```bash
docker build --build-arg VITE_GOOGLE_CLIENT_ID=<id> -t curio-frontend .
docker run -p 3000:80 curio-frontend
```

Multi-stage build: Node Alpine builds the SPA, nginx Alpine serves it and reverse-proxies `/api/` to the backend. For local dev, prefer `npm run dev` (native, faster HMR). See [`../docs/DEPLOY.md`](../docs/DEPLOY.md).
