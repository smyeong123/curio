# Curio — Deploy Checklist

A linear todo list for taking Curio from this repo to a public URL.
Tick boxes as you go. Estimated total time: **2–4 hours** if API key signups go smoothly.

---

## Phase 0 — Decide your soft-launch posture (5 min)

- [ ] Decide: launch **with** or **without** Google OAuth?
  - **Without**: skip phase 1A § Google. Email/password signup still works.
  - **With**: ~10 min in Google Cloud Console.
- [ ] Pick your production domain (e.g. `curio.news`). DNS A record to your server's IP must be set before TLS step.

---

## Phase 1 — Required API keys (45 min)

### Anthropic (Claude) — `CLAUDE_API_KEY`

- [ ] Create account: https://console.anthropic.com/
- [ ] Add billing card (pay-as-you-go — see the cost estimates in [SETUP_API_KEYS.md](./SETUP_API_KEYS.md))
- [ ] Settings → API Keys → "Create Key" → copy `sk-ant-api03-…`
- [ ] **Without this, no digests will be generated. The whole product breaks.**

### Resend (email) — `RESEND_API_KEY` + `RESEND_WEBHOOK_SECRET`

- [ ] Create account: https://resend.com/
- [ ] API Keys → create one with "Sending access" → copy `re_…`
- [ ] **Verify your sending domain** (mandatory for sending to real users):
  - [ ] Domains → Add Domain → enter `curio.news` (or whatever you own)
  - [ ] Add the 3 DNS records (SPF, DKIM, DMARC) at your DNS provider
  - [ ] Wait for green check (usually <1 hr)
  - [ ] Set `FROM_EMAIL` in your `.env.prod` to an address at **your** verified
        domain (e.g. `digest@your-domain.com`). The prod config reads it from the
        environment (`FROM_EMAIL`, default `digest@curio-news.dev`) — no need to
        hand-edit any YAML.
- [ ] Webhooks → Add Endpoint → URL `https://your-domain/api/v1/webhooks/email` →
      copy the signing secret → that's `RESEND_WEBHOOK_SECRET`

> Test mode (no domain verified) only sends to the email you signed up with.
> Every send to other addresses returns 403 — the admin UI will show
> `failCount` for those after the recent fix.

### NewsAPI — `NEWS_API_KEY`

- [ ] Create account: https://newsapi.org/register
- [ ] Copy API key from the dashboard
- [ ] **Read the TOS**: free tier prohibits commercial use. For a public/paid product
      you need the Business plan ($449/mo). Curio's usage is ~25 req/day,
      well within free quota — but the legal use is what matters.
- [ ] Without this: digests will still generate but with weaker source grounding
      (Claude works from its training set + first-party lab blogs only).

### Google OAuth — `GOOGLE_CLIENT_ID` (skip if not using; no client secret needed)

- [ ] https://console.cloud.google.com/ → create or pick a project
- [ ] APIs & Services → Credentials → Create Credentials → OAuth client ID
- [ ] Application type: **Web application**
- [ ] Authorized JavaScript origins: `https://your-domain.com` — Curio's Google login is a browser-side ID-token flow (GSI), so this is the setting that matters
- [ ] Leave **Authorized redirect URIs** empty — there is no server-side OAuth callback
- [ ] Copy the client ID (the client secret is never used — Curio validates ID tokens server-side)
- [ ] OAuth consent screen → publish (or keep in testing for first-party only)
- [ ] Note: also pass `VITE_GOOGLE_CLIENT_ID` to the frontend Docker build (see phase 4)

---

## Phase 2 — Generate self-signed secrets (2 min)

Run each of these and copy the output into your `.env.prod` (next phase):

```bash
openssl rand -base64 32   # → JWT_SECRET
openssl rand -base64 32   # → JWT_REFRESH_SECRET
openssl rand -base64 32   # → UNSUBSCRIBE_SECRET
openssl rand -base64 32   # → API_KEY_ENCRYPTION_KEY (BYOK at-rest cipher)
openssl rand -base64 24   # → POSTGRES_PASSWORD
openssl rand -base64 24   # → REDIS_PASSWORD
```

- [ ] Stored all six somewhere safe.
  - **Lose `JWT_SECRET`** → all live sessions invalidated; users must log back in.
  - **Lose `API_KEY_ENCRYPTION_KEY`** → every stored BYOK key becomes
    permanently unreadable. Users must re-add them. Treat this key like the
    Postgres password — back it up offline.

---

## Phase 3 — Build `.env.prod` locally (5 min)

- [ ] `cp .env.prod.example .env.prod`
- [ ] Open `.env.prod` and fill in every value above (each variable is explained in [ENV_VARIABLES.md](./ENV_VARIABLES.md)). Soft-launch template:

```bash
# DB
POSTGRES_DB=curio
POSTGRES_USER=curio_user
POSTGRES_PASSWORD=<from phase 2>

# Redis
REDIS_PASSWORD=<from phase 2>

# JWT + signing
JWT_SECRET=<from phase 2>
JWT_REFRESH_SECRET=<from phase 2>
UNSUBSCRIBE_SECRET=<from phase 2>
API_KEY_ENCRYPTION_KEY=<from phase 2>   # AES-256-GCM master key for BYOK

# Google OAuth (or leave blank to disable Google login)
GOOGLE_CLIENT_ID=...apps.googleusercontent.com

# AI
AI_PROVIDER=claude
CLAUDE_API_KEY=sk-ant-api03-...
GEMINI_API_KEY=
OPENAI_API_KEY=

# Email
RESEND_API_KEY=re_...
RESEND_WEBHOOK_SECRET=whsec_...
FROM_EMAIL=digest@your-domain.com   # must be at your verified sending domain

# News source
NEWS_API_KEY=...

# App URLs (use your real domain — do NOT use http:// in prod)
FRONTEND_URL=https://your-domain.com
BACKEND_URL=https://your-domain.com

# Frontend exposed port (80 if you'll TLS-terminate elsewhere)
FRONTEND_PORT=80

# Optional
SENTRY_DSN=
SENTRY_ENVIRONMENT=prod
SENTRY_TRACES_SAMPLE_RATE=0.1
```

- [ ] Confirm `.env.prod` is in `.gitignore` (it is — but double-check before any `git add .`)

---

## Phase 4 — Local sanity check (10 min)

Run before pushing to a server — catches every mistake locally:

- [ ] `cd backend && mvn test` → all backend tests pass
- [ ] `cd frontend && npm run test:unit -- --run` → all frontend tests pass
- [ ] `cd frontend && npm run build` → must end with `✓ built in <Nms>` and produce `dist/`
- [ ] Build the production images locally:
      `docker compose --env-file .env.prod build`
      (first build is slow — ~5 min for backend, ~2 min for frontend)
- [ ] Spin up the full stack locally to smoke test:
      `docker compose --env-file .env.prod up -d`
- [ ] Wait ~60s, then:
      `docker compose exec backend wget -qO- http://localhost:8080/actuator/health` → `{"status":"UP"}`
      `curl -s http://localhost/` → SPA HTML
      (nginx blocks `/actuator` at the edge — health is checked inside the container.)
- [ ] Tear down: `docker compose --env-file .env.prod down`

---

## Phase 5 — Provision the server (15 min)

Pick one host. Curio fits comfortably on:
- DigitalOcean basic droplet (4 GB RAM, $24/mo)
- Hetzner CX22 (4 GB RAM, ~€5/mo)
- AWS t3.small (more if you want managed RDS)

- [ ] Create the VM, note its public IP
- [ ] Point your domain's `A` record at that IP. Wait for propagation (`dig your-domain.com` → returns the IP)
- [ ] SSH in
- [ ] Install Docker (Ubuntu/Debian):
  ```bash
  curl -fsSL https://get.docker.com | sh
  sudo usermod -aG docker $USER && newgrp docker
  ```
- [ ] Verify: `docker --version && docker compose version`
- [ ] Open firewall: `ufw allow 80,443/tcp` (or your cloud's security group)

---

## Phase 6 — Ship the code (5 min)

Pick one:

**A) git clone + build on server (simplest)**

- [ ] `git clone <your-repo-url> ~/curio` on the server
- [ ] `cd ~/curio`
- [ ] Copy your `.env.prod` to the server (e.g. `scp .env.prod user@server:~/curio/`)

**B) Build images locally + push to a registry (faster restarts)**

- [ ] Tag images: `docker tag curio-backend:latest ghcr.io/you/curio-backend:v1`
- [ ] Push: `docker push ghcr.io/you/curio-backend:v1` (and same for frontend)
- [ ] On server: set `BACKEND_IMAGE` and `FRONTEND_IMAGE` in `.env.prod` to the registry URLs

---

## Phase 7 — Boot the stack (5 min)

On the server, in `~/curio`:

- [ ] `docker compose --env-file .env.prod pull && docker compose --env-file .env.prod up -d`
  (no `--build` on a 1 GB box — it OOMs; images are pre-built and pushed per Phase 6)
- [ ] Watch it come up: `docker compose ps` — wait until **all four** show `(healthy)`:
  - `curio-postgres` (healthy after ~10s)
  - `curio-redis` (healthy after ~5s)
  - `curio-backend` (healthy after ~40s — Spring Boot startup)
  - `curio-frontend` (healthy after ~5s)
- [ ] Check logs for errors: `docker compose logs --tail=50 backend`
  - Must see: `Started CurioApplication in N seconds`
  - Must see: Flyway migrations applied (V1–V29)
- [ ] Tail front: `docker compose logs --tail=20 frontend` — should show nginx ready

---

## Phase 8 — TLS / HTTPS (15 min)

You can't ship without TLS — plain `docker compose up -d` serves HTTP on :80 only,
so JWTs and passwords would travel in clear. Use the bundled Caddy overlay. The
`Caddyfile` reads the domain from `$CURIO_DOMAIN`, so you do **not** edit it.

- [ ] Add to `.env.prod`:
  ```
  CURIO_DOMAIN=your-domain.com
  ACME_EMAIL=you@your-domain.com
  ```
- [ ] Confirm ports 80 **and** 443 are open to the internet (security group / `ufw`).
- [ ] Deploy with the wrapper (brings up the stack + Caddy TLS overlay):
  ```bash
  ./scripts/deploy-prod.sh
  ```
  Equivalent manual command:
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.tls.yml --env-file .env.prod pull
  docker compose -f docker-compose.yml -f docker-compose.tls.yml --env-file .env.prod up -d
  ```
- [ ] Caddy will auto-fetch a Let's Encrypt cert (~30s). Watch with `docker compose logs caddy`
- [ ] Visit `https://your-domain.com` — should serve the Curio home page
- [ ] Verify cert: `curl -vI https://your-domain.com 2>&1 | grep -i -E "ssl|cert"`

### Alternative — Cloudflare origin TLS (nginx terminates with a Cloudflare Origin Cert)

Use this instead of the Caddy overlay when the site is **proxied through Cloudflare**
(orange cloud). ⚠️ Do NOT rely on Cloudflare's default "Flexible" SSL mode: it encrypts
only browser→Cloudflare and talks to your origin over **plaintext HTTP**, so JWTs and
passwords cross the public internet in the clear on the Cloudflare→origin hop. Close that
hop by giving nginx a Cloudflare Origin Certificate and switching Cloudflare to **Full (strict)**.

- [ ] Cloudflare dashboard → **SSL/TLS → Origin Server → Create Certificate** (accept the
      defaults; 15-year cert). Copy the certificate and private key.
- [ ] On the server, install them where `TLS_CERT_DIR` will point (default `/etc/curio/tls`):
  ```bash
  sudo mkdir -p /etc/curio/tls
  sudo tee /etc/curio/tls/origin.pem >/dev/null   # paste the certificate, save
  sudo tee /etc/curio/tls/origin.key >/dev/null   # paste the private key, save
  sudo chmod 600 /etc/curio/tls/origin.key
  ```
- [ ] Add to `.env.prod`: `CURIO_DOMAIN=your-domain.com` and `TLS_CERT_DIR=/etc/curio/tls`.
- [ ] Cloudflare dashboard → **SSL/TLS → Overview → Full (strict)**, and **Edge Certificates →
      Always Use HTTPS → On** (also turn on **HSTS** once you've confirmed HTTPS works).
- [ ] Lock the security group so inbound **:80 and :443 accept only Cloudflare IP ranges**
      (<https://www.cloudflare.com/ips/>) — this removes the direct-to-IP plaintext path.
- [ ] Deploy with the Cloudflare overlay (not the Caddy one):
  ```bash
  docker compose -f docker-compose.yml -f docker-compose.cf-tls.yml --env-file .env.prod up -d
  # sanity-check the merged config + nginx syntax first:
  docker compose -f docker-compose.yml -f docker-compose.cf-tls.yml --env-file .env.prod config >/dev/null
  docker compose -f docker-compose.yml -f docker-compose.cf-tls.yml --env-file .env.prod \
    exec frontend nginx -t
  ```
- [ ] Verify: `curl -sSI https://your-domain.com | head -1` → `200`, and the bare origin IP
      over plain HTTP should now be **unreachable** from anywhere but Cloudflare.
- [ ] Optional hardening: enable **Authenticated Origin Pulls** (Cloudflare mTLS) and uncomment
      the `ssl_client_certificate` / `ssl_verify_client` lines in `frontend/nginx-tls.conf` so
      the origin answers Cloudflare *only*, even if the firewall is later loosened.

Either overlay keeps `app.cookie.secure=true` (already on in the prod profile) valid, because
the browser-facing scheme is HTTPS end to end.

---

## Phase 9 — First-run operations (10 min)

- [ ] Visit `https://your-domain.com`, click **Subscribe**, register your own admin account
- [ ] On the server, promote yourself to admin:
  ```bash
  docker compose exec postgres psql -U $POSTGRES_USER -d $POSTGRES_DB \
    -c "UPDATE users SET is_admin = true WHERE email = 'you@your-domain.com';"
  ```
- [ ] Sign out + back in to pick up the admin claim
- [ ] You should now see **Newsroom** in the sidebar
- [ ] Visit `/admin/dashboard`, click **Run now** on "Generate digests" (no topic filter) — kicks off the first batch
  - Wait ~10s per user. Check the result panel — `successCount` should match subscriber count
- [ ] Click **Run now** on "Send the morning post" — emails the digests
  - If Resend is in test mode, expect `failCount` for everyone except your verified address. **This is fine for soft launch.**
- [ ] Verify a real digest by visiting `/dashboard/archive` while signed in as a non-admin test account

---

## Phase 10 — Schedule the cron (already on, just verify)

The three jobs are wired with `@Scheduled` and run automatically:

- **05:00 Asia/Seoul (20:00 UTC)** — Generate the day's digest for everyone, worldwide (one run per day)
- **Hourly** — email each user that digest once their local time reaches their delivery hour (default 06:00), at most once per local day
- **00:00 UTC** — Delete digests + quizzes older than 30 days

ShedLock prevents double-runs if you scale to multiple backend pods. No action needed.

- [ ] After the next 05:00 KST tick (or a manual run), confirm `/admin/jobs/status` shows
      `digest-generation: SUCCESS @ <recent timestamp>`

---

## Phase 11 — Smoke test (5 min)

From your laptop:

- [ ] Backend health (internal — nginx blocks `/actuator` at the edge by design):
      `docker compose exec backend wget -qO- http://localhost:8080/actuator/health` → `{"status":"UP"}`
      (External `https://your-domain/actuator/health` returns 404 — that's intentional, not a failure.)
- [ ] Sign in as your admin account, take a quiz end-to-end
- [ ] Sign out, hit forgot-password, check the email arrived (only works if Resend domain is verified or you used your verified address)
- [ ] Try the unsubscribe link in that email → must land on the unsubscribe confirmation page
- [ ] Sign back in, go to **Settings**, toggle "Daily email digest" off and on

---

## Phase 12 — Day-2 operational hygiene (do within first week)

- [ ] **Backups**: Postgres lives on a *local docker volume* — losing the host loses the DB.
      Use the bundled script (timestamped, gzipped, auto-pruned):
  ```bash
  ./scripts/pg_backup.sh                       # one-off
  # cron (daily 03:30, before the 06:00 digest job):
  30 3 * * *  cd /opt/curio && ./scripts/pg_backup.sh >> /var/log/curio-backup.log 2>&1
  ```
  Tune `RETENTION_DAYS` (default 14) and uncomment the S3/rclone `SYNC` line in the script
  to push dumps off-box. Restore with `./scripts/db_restore.sh <dump.sql.gz>`.
  **Strongly preferred for real production:** a managed Postgres (Neon / Supabase / RDS) with
  point-in-time recovery, instead of the single-box volume.
- [ ] **Sentry**: sign up at sentry.io, create a project, paste DSN into `.env.prod` `SENTRY_DSN`, restart backend. Errors will start streaming.
- [ ] **Anthropic spend cap**: set a monthly usage cap in Anthropic console so a runaway batch can't bankrupt you.
- [ ] **k6 baseline**: run the bundled load tests (`scripts/k6/`) against your production URL once,
      so you have a "what's normal" reading before you scale usage.

---

## Phase 13 — Bring-Your-Own-Key (BYOK) verification (5 min, optional)

After launch, sanity-check the BYOK feature works in your environment:

- [ ] Sign in as a test user (must have a password — Google-only accounts can't manage keys until they set one in Settings).
- [ ] Settings → § IV Bring your own key → **Add a key**.
- [ ] Pick provider, paste a real key, enter password, leave "Validate before saving" on.
- [ ] Save → confirms in <8 s with the masked preview shown.
- [ ] Trigger a manual digest gen for that user (admin → Run now). Watch backend logs:
      `BYOK key updated for user {id}` followed by Anthropic 200 — billed to **the user's**
      Anthropic account, not yours.
- [ ] Remove the key (re-prompts for password). BYOK badge disappears immediately.

If "Validate before saving" returns false even with a known-good key, check:
- Network egress from the backend container (must reach `api.anthropic.com` etc.)
- Provider's API status page
- Backend logs for `Key validation for {provider} could not complete: ...`

---

## Appendix A — Known caveats (won't block launch — be aware)

1. **Resend test mode**: until you verify a sending domain, all subscriber sends 403. The admin UI now shows the real `failCount` honestly — don't be alarmed by it.
2. **Lab-blog feeds rot silently**: feed URLs were last re-verified 2026-07-05 (Anthropic replaced with a Google News RSS query, Mistral moved to `/rss.xml`). If digests thin out, curl the URLs in `backend/src/main/java/com/curio/news/service/LabBlogRegistry.java` — digests still generate via NewsAPI fallback, but lab-blog signal disappears without any error.
3. **Resend webhooks need TLS**: webhook deliveries from Resend won't accept self-signed certs. Real Let's Encrypt cert (phase 8) is mandatory before turning on webhooks.
4. **First Anthropic call is slow**: cold cache + Claude latency means the first digest of the day takes ~15s per user. Bulkhead caps concurrency at 4 so a 50-user batch takes ~3–4 minutes. nginx and Axios timeouts are already raised to 600s for admin paths.

---

## Appendix B — Rollback plan (if something goes wrong)

The cleanest revert is at the Docker layer — your data is in named volumes (`postgres_data`, `redis_data`) and survives container deletion.

- [ ] `docker compose down` — stops containers, keeps data
- [ ] `docker compose down -v` — **destroys** the volumes too (only do this for a clean reset)
- [ ] To rollback to a previous code version:
  - Change `BACKEND_IMAGE` / `FRONTEND_IMAGE` tags in `.env.prod` to the previous pushed tags, then `docker compose --env-file .env.prod pull && docker compose --env-file .env.prod up -d` (never `--build` on the 1 GB box — it OOMs; images build on your Mac)
- [ ] Flyway migrations are forward-only — if you need to roll back a schema change, restore from a Postgres backup (see Phase 12)

---

## Final pre-launch checklist (the 60-second version)

- [ ] All four required keys filled in (`CLAUDE_API_KEY`, `RESEND_API_KEY`, `NEWS_API_KEY`, `RESEND_WEBHOOK_SECRET`)
- [ ] All six generated secrets filled in (Phase 2 — JWT × 2, UNSUBSCRIBE, **API_KEY_ENCRYPTION**, Postgres, Redis)
- [ ] Resend sending domain verified
- [ ] DNS A record live
- [ ] `docker compose ps` shows 4 healthy containers (5 with the Caddy TLS overlay — Caddy has no healthcheck, so it shows `Up` rather than `healthy`)
- [ ] `https://your-domain.com` loads + cert is valid
- [ ] You can register, sign in, see today's digest, take a quiz
- [ ] Forgot-password email lands in your inbox
- [ ] `/admin/dashboard` shows recent timestamps for all 3 jobs
- [ ] `pg_dump` backup script tested

Ship it.
