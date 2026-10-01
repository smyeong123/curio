# Environment Variables Reference

This document explains every variable in [`.env.prod.example`](../.env.prod.example) — what it
is, **why** it's needed, and **how** it works.

> **Local dev needs almost none of these** — copy `.env.dev.example` → `.env.dev`
> once (the infra compose fail-fasts without `POSTGRES_PASSWORD`), then run
> `docker compose --env-file .env.dev -f docker-compose.infra.yml up -d` for
> Postgres + Redis only.
> These matter for **production**: copy `.env.prod.example` → `.env.prod`, fill in, then
> `docker compose --env-file .env.prod up -d`.

---

## Quick reference

| Variable | Group | Required in prod? | Notes |
|---|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | Database | ✅ (password) | DB name/user/password |
| `REDIS_PASSWORD` | Redis | ✅ | cache auth |
| `JWT_SECRET` / `JWT_REFRESH_SECRET` | Auth | ✅ | token signing keys |
| `JWT_REFRESH_EXPIRATION` | Auth | optional (default `10800000` ms = 3 h) | sliding idle-timeout window for refresh tokens |
| `AUTH_EMAIL_VERIFICATION_*` (4) | Auth | optional | login 2FA code step (default on) |
| `GOOGLE_CLIENT_ID` | OAuth | optional | Google sign-in (ID-token flow — no secret); empty disables it |
| `AI_PROVIDER` + `*_API_KEY` | AI | ✅ (one key) | claude \| gemini \| openai |
| `RESEND_API_KEY` / `RESEND_WEBHOOK_SECRET` | Email | ✅ | send + verify webhooks |
| `NEWS_API_KEY` | News | ✅ | source articles |
| `FRONTEND_URL` / `BACKEND_URL` / `UNSUBSCRIBE_SECRET` | App URLs | ✅ (secret) | links + token signing |
| `CURIO_DOMAIN` / `ACME_EMAIL` | TLS | only with `docker-compose.tls.yml` | Caddy + Let's Encrypt |
| `API_KEY_ENCRYPTION_KEY` | BYOK | ✅ | at-rest encryption; **don't lose it** |
| `FRONTEND_PORT` | Frontend | default 80 | host port for nginx |
| `APP_DEFAULT_DELIVERY_TIMEZONE` | Email | optional (default `Asia/Seoul`) | fallback zone for users with no captured timezone |
| `CLAUDE_MODEL` | AI | optional (default `claude-sonnet-4-6`) | Claude model selection |
| `OPENAI_MODEL` | AI | optional (default `gpt-4o-mini`) | OpenAI model selection |
| `RESEND_WEBHOOK_TOLERANCE_SECONDS` | Email | optional (default `300`) | webhook signature timestamp tolerance |
| `UNSUBSCRIBE_TOKEN_TTL_HOURS` | Email | optional (default `720`) | unsubscribe token lifetime (hours) |
| `SENTRY_RELEASE` | Observability | optional | release version for Sentry tracking |
| `FROM_EMAIL` | Email | optional | sender email address |
| `APP_RATE_LIMIT_TRUSTED_PROXY_HOPS` | Security | optional (default `1`) | X-Forwarded-For hops to trust for client IP |
| `SENTRY_*` (3) | Observability | optional | error tracking |
| `BACKEND_IMAGE` / `FRONTEND_IMAGE` | Deploy | optional | pre-built images vs. local build |

---

## Database (PostgreSQL)

- **`POSTGRES_DB`** — database name. Default `curio`.
- **`POSTGRES_USER`** — DB username. Default `curio_user`.
- **`POSTGRES_PASSWORD`** — DB password. **Must change** in prod.

These initialize the Postgres container and are reused by the backend's JDBC
connection. The backend reads them as `DB_NAME` / `DB_USER` / `DB_PASSWORD`.

## Redis (cache)

- **`REDIS_PASSWORD`** — password for the Redis cache. **Must change.**

Redis caches AI-generated content (12-hour TTL) to cut API costs. The password
protects the cache from unauthorized access on a shared network.

---

## JWT — `JWT_SECRET` & `JWT_REFRESH_SECRET`

**Why:** The app uses *stateless* auth. On login the server hands the user a signed
token (JWT) instead of storing a session, and trusts that token on every later
request. The secret is what makes that trust possible.

**How it works:**
1. On login, the server builds a token (user ID + expiry) and **signs it** with
   `JWT_SECRET` using HMAC — the signature is `hash(token_data + secret)`.
2. The user sends the token in the `Authorization` header on every request.
3. The server recomputes the signature with its secret. Match = genuine and
   untampered. If someone edits the token (e.g. swaps in an admin's ID), the
   signature won't match and it's rejected.
4. **Anyone who knows the secret can forge a token for any user** — so it must be
   long, random, and never committed. `openssl rand -base64 64` = 64 bytes of
   cryptographic randomness.

**Why two secrets:** Access tokens are short-lived (15 min) and sent constantly, so
more exposed. Refresh tokens live ~3 hours as a *sliding* idle timeout (tunable via
`JWT_REFRESH_EXPIRATION`, ms; default `10800000` = 3h) and only mint new access
tokens — each refresh rotates the token and resets the window, so an active session
survives and expires after ~3h of inactivity. Separate keys mean compromising one
context doesn't break the other (defense-in-depth).

---

## Login 2FA — `AUTH_EMAIL_VERIFICATION_*`

Email/password sign-in is two-step: the password is validated, then a 6-digit
code is emailed and must be entered before a session is issued. Google OAuth is
never gated by this. All four variables are optional — the defaults below apply.

| Variable | Default | Meaning |
|---|---|---|
| `AUTH_EMAIL_VERIFICATION_ENABLED` | `true` | Toggle the whole code step (set `false` for single-step login) |
| `AUTH_EMAIL_VERIFICATION_MAX_ATTEMPTS` | `5` | Wrong-code budget per challenge; at 0 the challenge locks and the client offers a password reset |
| `AUTH_EMAIL_VERIFICATION_CODE_LENGTH` | `6` | Digits in the code |
| `AUTH_EMAIL_VERIFICATION_CODE_TTL_MINUTES` | `10` | Code lifetime |

Requires working email delivery (Resend). In local dev the code is also logged
(`auth.email-verification.log-code: true` in `application-dev.yml`), so you can
complete login without configuring email.

---

## Google OAuth — `GOOGLE_CLIENT_ID`

OAuth client id for Google sign-in. Used ONLY to validate the Google ID
token's `aud` claim server-side (`app.google-client-id`); login is a
browser-side Google Identity Services flow, so there is **no client secret**
and no redirect URI. Leave empty to disable Google login — email/password
auth is unaffected.

---

## AI Provider — `AI_PROVIDER` + `CLAUDE_API_KEY` / `GEMINI_API_KEY` / `OPENAI_API_KEY`

- **`AI_PROVIDER`** — selects the `AiService` implementation: `claude` (default),
  `gemini`, or `openai`.
- **`*_API_KEY`** — credential for the chosen provider; used for news
  summarization and quiz generation. You only need the key matching `AI_PROVIDER`.

---

## Email (Resend) — `RESEND_API_KEY` & `RESEND_WEBHOOK_SECRET`

Two opposite directions of communication.

**`RESEND_API_KEY` (you → Resend):**
- **Why:** Curio sends daily digest emails but doesn't run its own mail server
  (deliverability is hard). It hands emails to Resend.
- **How:** The hourly email job (which emails each user at their local delivery hour)
  makes an HTTPS call to Resend's API with this key in the header, authenticating the
  Curio account. Without it, Resend rejects the send.

**`RESEND_WEBHOOK_SECRET` (Resend → you):**
- **Why:** Resend reports back "opened / clicked / bounced" by POSTing to your
  *public* webhook URL. Anyone could POST fake events, so you must prove the request
  truly came from Resend.
- **How:** Resend signs each payload with this shared secret (HMAC-SHA256) and
  includes the signature in a header. `WebhookSignatureVerifier` recomputes it over
  the received body. Match = genuine; mismatch = forged, rejected.
- **Blocks boot in prod:** If blank, the endpoint can't be protected, so the app
  refuses to start rather than expose an unauthenticated webhook.

> Symmetry: the **API key** is a credential *you present* to them; the **webhook
> secret** verifies *what they send you*.

---

## News — `NEWS_API_KEY`

API key for the News API that fetches source articles feeding the AI pipeline.

---

## App URLs — `FRONTEND_URL`, `BACKEND_URL`, `UNSUBSCRIBE_SECRET`

- **`FRONTEND_URL`** — public site URL (email links, redirects).
- **`BACKEND_URL`** — public API URL.
- **`UNSUBSCRIBE_SECRET`** — HMAC key signing unsubscribe tokens in emails so they
  can't be forged/tampered. **Must change.**

---

## TLS — `CURIO_DOMAIN` & `ACME_EMAIL`

**Why:** Used only with the `docker-compose.tls.yml` stack, which adds **Caddy** as
a reverse proxy that auto-provisions a real, browser-trusted HTTPS certificate from
Let's Encrypt for free. Without HTTPS, browsers warn users and JWTs/passwords travel
in plaintext.

**How it works (ACME protocol):**
1. Caddy tells Let's Encrypt "I want a cert for **`CURIO_DOMAIN`**."
2. Let's Encrypt issues a challenge: prove you control the domain.
3. Caddy answers automatically; Let's Encrypt verifies and issues a ~90-day cert,
   then auto-renews it.
4. **`ACME_EMAIL`** is your registration contact for expiry/policy notices.

**Fails fast:** The compose file uses `:?` ("abort `up` if unset") — a TLS proxy
with no domain is useless, so it stops you upfront. `CURIO_DOMAIN`'s DNS must point
at your server or the challenge fails.

---

## BYOK encryption — `API_KEY_ENCRYPTION_KEY`

**Why:** BYOK = "Bring Your Own Key." Users paste *their own* AI provider keys so
usage bills to them. Those keys are valuable secrets — storing them plaintext means
a DB leak exposes every user's key.

**How it works:**
1. On save, the backend **encrypts** the key with `API_KEY_ENCRYPTION_KEY`
   (symmetric — same key encrypts/decrypts) before writing to the DB. Stored value
   is ciphertext.
2. When calling the AI provider on the user's behalf, it reads and **decrypts** the
   ciphertext to recover the real key.
3. The key lives in the environment, **not** the DB — stealing the DB alone yields
   only useless ciphertext.

**Warnings:**
- *Required in prod (won't start without it):* running BYOK with no key means
  plaintext storage or crashes — both unacceptable. (Dev has a fallback key.)
- *Losing it = unrecoverable keys:* symmetric encryption means only this exact key
  decrypts stored values. Lose/rotate it and all ciphertext is permanently
  undecryptable — users must re-enter keys. **Back it up.**
  Generate with `openssl rand -base64 32` (32 bytes = AES-256 strength).

---

## Frontend port — `FRONTEND_PORT`

**What:** The **host** port that serves the site; compose maps `${FRONTEND_PORT}:80` onto the internal nginx container.
**Why:** Change it if 80 is taken, or front it with Caddy when using TLS.
**Default:** `80` (standard HTTP).

---

## AI models — `CLAUDE_MODEL` & `OPENAI_MODEL`

**What:** Which model the active provider calls for news summarization and quiz generation. Each is ignored unless `AI_PROVIDER` matches.
**Why:** Lets you bump the model without a code change (this is how the 2026-06-24 Claude model 404 was fixed).
**Default:** `CLAUDE_MODEL=claude-sonnet-4-6`, `OPENAI_MODEL=gpt-4o-mini`.

---

## Email & webhooks — `RESEND_WEBHOOK_TOLERANCE_SECONDS` & `UNSUBSCRIBE_TOKEN_TTL_HOURS`

**What:** `RESEND_WEBHOOK_TOLERANCE_SECONDS` is the accepted clock skew (seconds) on webhook signature timestamps; `UNSUBSCRIBE_TOKEN_TTL_HOURS` is how long unsubscribe links stay valid (hours).
**Why:** The tolerance window blocks replayed webhook payloads; the token TTL bounds how long an old email's unsubscribe link works.
**Default:** `300` (5 minutes) and `720` (30 days).

---

## Email sender — `FROM_EMAIL`

**What:** Sender address for all transactional email (digests, password resets, verification codes).
**Why:** Must belong to a Resend-verified domain or sends fail with 403.
**Default:** prod `digest@curio-news.dev` (`application-prod.yml`), dev `onboarding@resend.dev`, with a `no-reply@curio-news.dev` code-level fallback in `EmailService`.

---

## Delivery timezone fallback — `APP_DEFAULT_DELIVERY_TIMEZONE`

**What:** IANA timezone used to compute a user's delivery hour when no timezone was ever captured for them (timezone normally auto-follows the device — V26).
**Why:** The hourly `EmailSendJob` needs *some* zone to gate each user's send (default delivery hour 06:00); this is the last-resort fallback.
**Default:** `Asia/Seoul`.

---

## Rate limiting — `APP_RATE_LIMIT_TRUSTED_PROXY_HOPS`

**What:** How many hops (from the right) of the `X-Forwarded-For` header to trust when extracting the client IP for rate limiting. Set it to the number of proxies that append to XFF in front of the backend.
**Why:** Getting this wrong is a footgun — too low and the limiter keys on a proxy's constant IP, collapsing every user onto one shared bucket so a few requests lock out auth site-wide.
**Default:** `1` = the base `docker-compose.yml` topology (nginx → backend, one appended hop).
  - `docker-compose.yml` (nginx → backend): **`1`** (the default — no override needed).
  - `docker-compose.tls.yml` (Caddy → nginx → backend): **`2`** — this overlay already sets it on the `backend` service for you, so no manual action is needed when using it.
  - Add one per extra appending proxy (e.g. `3` for CDN → Caddy → nginx → backend).

---

## Sentry release — `SENTRY_RELEASE`

**What:** Release version string stamped on all Sentry events.
**Why:** Lets you filter issues by version and see which release introduced a regression.
**Default:** unset — events are tagged with the build's default.

---

## Sentry (optional) — `SENTRY_DSN`, `SENTRY_ENVIRONMENT`, `SENTRY_TRACES_SAMPLE_RATE`

Error/exception tracking so you learn about crashes from a dashboard, not users.
Optional — Curio runs fine without it.

- **`SENTRY_DSN`** — "Data Source Name," a URL telling the SDK which project to send
  errors to and authenticating them. Blank = Sentry disabled (SDK no-ops).
- **`SENTRY_ENVIRONMENT`** — label (`prod`, `staging`…) stamped on reports for
  filtering.
- **`SENTRY_TRACES_SAMPLE_RATE`** — fraction of requests traced for performance.
  `0.1` = 10%; `1.0` = everything; `0` = errors only, no tracing.

---

## Pre-built images (optional, commented out) — `BACKEND_IMAGE` / `FRONTEND_IMAGE`

**Why:** By default `docker compose up` **builds** backend and frontend from source
— slow, and needs Maven/Node present. These let you **pull a ready-made image** from
a registry instead.

**How it works:**
- Commented out → compose falls back to its `build:` instruction (compile locally).
- Set them → compose uses `image: ${BACKEND_IMAGE}` and just downloads that tag.
- **Typical workflow:** CI builds the image once, pushes it tagged `:latest`/version
  to the registry; prod just pulls and runs. Faster deploys, identical artifact
  everywhere, no build tools on the prod box.
