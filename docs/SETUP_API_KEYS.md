# API Keys Setup Guide

Where to sign up for each external service Curio uses, with pricing notes. This
guide covers **obtaining** the keys only. For *where each value goes* and what it
does, see [ENV_VARIABLES.md](./ENV_VARIABLES.md); for the end-to-end local
walkthrough, see [SETUP.md](./SETUP.md); for production, see [DEPLOY.md](./DEPLOY.md).

## Services at a glance

| Service | Purpose | Free tier | Setup time | Variable |
|---------|---------|-----------|------------|----------|
| Claude API | News summarization & quiz generation | Pay-as-you-go, $5 free credits | 5 min | `CLAUDE_API_KEY` |
| Resend | Transactional email (digests, auth) | 100 emails/day, 3,000/month | 10 min | `RESEND_API_KEY` |
| News API | Source article fetching | 100 requests/day | 2 min | `NEWS_API_KEY` |
| Google OAuth | "Sign in with Google" (optional) | Free | 10 min | `GOOGLE_CLIENT_ID` |

**Total estimated setup time:** ~30 minutes. Only an AI provider key and
`NEWS_API_KEY` are needed for core digests; the rest enable email and social login.

---

## 1. Claude API (Anthropic)

AI-powered news summarization and quiz generation.

1. Visit [console.anthropic.com](https://console.anthropic.com)
2. Create an account or sign in
3. **Settings → API Keys → Create Key**
4. Name it (e.g. `Curio Development`)
5. Copy the key (starts with `sk-ant-...`) — this is your `CLAUDE_API_KEY`

**Pricing / notes:**
- Model: `claude-sonnet-4-6` (override with `CLAUDE_MODEL`)
- ~a few tenths of a cent per digest (2-3 summaries per topic, round-robined and capped at 8 stories, plus a 5-question quiz)
- $5 free credits for new accounts; rate limit 50 req/min (ample for MVP)

---

## 2. Resend (email)

Transactional email delivery — daily digests and auth emails.

1. Visit [resend.com](https://resend.com) and sign up
2. **API Keys → Create API Key** (Sending access)
3. Copy the key (starts with `re_...`) — this is your `RESEND_API_KEY`

**Domain verification (for production):**
1. **Domains → Add Domain** → enter a domain you own
2. Add the DNS records Resend shows (SPF, DKIM, DMARC) at your DNS provider
3. Wait for the green check (usually 5-30 min)

**Pricing / notes:**
- Free tier: 100 emails/day, 3,000/month; $20/mo for 50,000
- **Test mode** (no verified domain) only delivers to the address you signed up with — every other recipient returns 403
- Production also needs `RESEND_WEBHOOK_SECRET` for open/click tracking — see [DEPLOY.md](./DEPLOY.md)

---

## 3. News API

Source article fetching for digest grounding.

1. Visit [newsapi.org/register](https://newsapi.org/register)
2. Register (name, email, country)
3. Copy the API key from the email/dashboard (32-char hex) — this is your `NEWS_API_KEY`

**Pricing / notes:**
- Free tier: 100 req/day, 1 req/sec — enough for dev (Curio uses ~25 req/day)
- The free tier prohibits commercial use; the Business plan is $449/mo. [GNews](https://gnews.io) is a free alternative (update `NewsApiClient.java` to switch endpoints)
- Optional: digests still generate without it, but with weaker source grounding (Claude works from its training set + first-party lab blogs)

---

## 4. Google OAuth 2.0

"Sign in with Google". **Optional** — email/password signup works without it.

> **How Curio's Google login actually works:** the frontend uses Google Identity
> Services (GSI) to obtain an **ID token** in the browser and posts it to the
> backend, which verifies it against Google's `tokeninfo` endpoint (audience +
> issuer checks). There is **no** server-side OAuth redirect/callback, so you do
> **not** configure an "Authorized redirect URI" — what matters is **Authorized
> JavaScript origins** (the site origin the GSI script runs on).

1. Visit [Google Cloud Console](https://console.cloud.google.com)
2. **Select Project → New Project** → name it (e.g. `Curio`) → **Create**
3. **APIs & Services → OAuth consent screen**: User Type **External**, fill in app name + support/developer email → **Save**
4. **APIs & Services → Credentials → Create Credentials → OAuth client ID**
   - Application type: **Web application**
   - **Authorized JavaScript origins:**
     - Development: `http://localhost:5173`
     - Production: `https://your-domain.com`
   - Leave **Authorized redirect URIs** empty — Curio doesn't use the redirect flow
   - **Create**
5. Copy the **Client ID** (ends in `.apps.googleusercontent.com`). It is used in two places:
   - Backend `GOOGLE_CLIENT_ID` — verifies the ID token's audience
   - Frontend `VITE_GOOGLE_CLIENT_ID` — initializes the GSI client

**Notes:**
- Scopes: `profile`, `email`
- The generated **Client Secret** is NOT needed anywhere — the backend validates ID tokens via Google's tokeninfo endpoint and has no `GOOGLE_CLIENT_SECRET` config. Only the client id is used.
- For >100 users, submit the consent screen for verification.

---

## 5. AI provider selection (optional)

The backend defaults to Claude. To use Gemini or OpenAI instead, set `AI_PROVIDER`
and supply the matching key (only one provider is active at a time):

- **Gemini** — create a key at [aistudio.google.com/apikey](https://aistudio.google.com/apikey) → `GEMINI_API_KEY`, `AI_PROVIDER=gemini`
- **OpenAI** — create a key at [platform.openai.com/api-keys](https://platform.openai.com/api-keys) → `OPENAI_API_KEY`, `AI_PROVIDER=openai`

All three providers implement the same summarization + quiz generation. See
[ENV_VARIABLES.md](./ENV_VARIABLES.md) for the full provider config.

---

## Where the values go

This guide stops at obtaining the keys. To wire them up:

- **Local dev:** put backend keys in `backend/.env` and `VITE_GOOGLE_CLIENT_ID` in `frontend/.env.local` — see [SETUP.md](./SETUP.md).
- **Every variable explained** (names, defaults, security notes, plus generated secrets like `UNSUBSCRIBE_SECRET` and `JWT_SECRET`): [ENV_VARIABLES.md](./ENV_VARIABLES.md).
- **Production:** [DEPLOY.md](./DEPLOY.md) has the full `.env.prod` walkthrough and secret generation.

**Never commit keys** — `.env*` files are gitignored. Use separate keys for dev
and prod, rotate them ~every 90 days, and set billing alerts on Claude and News API.

---

## Cost estimates

| Phase | Claude | Resend | News API | Google | Total |
|-------|--------|--------|----------|--------|-------|
| Development (2 weeks) | $0 (free credits) | $0 | $0 | $0 | **$0** |
| Production (~500 users) | ~$150 | $20 | $0 (free tier)\* | $0 | **~$170/mo** |

\*News API's free tier prohibits commercial use — budget $449/mo or swap providers for a real launch.

---

## Verifying each integration

After wiring up the keys (per [SETUP.md](./SETUP.md)):

- **Database + backend boot:** `cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev` → look for Flyway migrations applied and `Started CurioApplication`.
- **Claude (digest gen):** as an admin user, `POST /api/v1/admin/generate-digests` (or **Run now** in `/admin/dashboard`) → check the success/failure counts.
- **Resend (email):** `POST /api/v1/admin/send-emails` (or the dashboard) → check your inbox / backend logs.
- **Google sign-in:** open `http://localhost:5173/login`, click **Sign in with Google** → the GSI prompt/popup appears (no full-page redirect); consent returns you to the dashboard.

---

## Troubleshooting

- **"Invalid API key":** re-copy without stray spaces; confirm the key is activated (some services require email verification).
- **Google sign-in prompt never appears:** add your frontend origin to **Authorized JavaScript origins** (not a redirect URI) and confirm `VITE_GOOGLE_CLIENT_ID` is set. See § 4.
- **Resend emails not arriving:** check spam; in test mode only your signup address receives mail (all others 403); review the Resend dashboard for bounces/complaints.
- **News API rate-limited:** free tier is 100 req/day, 1 req/sec — cache/store articles to reduce calls.

---

**Questions?** Open an issue or contact the maintainer.
