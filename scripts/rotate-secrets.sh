#!/usr/bin/env bash
#
# Generate fresh values for the secrets Curio owns (JWT signing, unsubscribe
# HMAC, BYOK at-rest key). Prints to stdout only — it NEVER edits .env.prod or
# touches the live box. Copy the values you want into .env.prod, then redeploy.
#
# Scope note — three classes of secret, handled differently:
#   1. App-owned, safe to swap via env + restart  → generated below.
#   2. Provider-issued (Claude/Resend/NewsAPI/Google) → rotate in the provider
#      dashboard, paste the new value, then revoke the old. Listed below.
#   3. Datastore passwords already baked into a volume (Postgres) → env change
#      alone does NOT rotate them; see the note at the end.
#
# Usage:
#   ./scripts/rotate-secrets.sh
set -euo pipefail

command -v openssl >/dev/null || { echo "openssl not found" >&2; exit 1; }

# JWT secrets must be >=32 bytes, distinct, and not the dev placeholders
# (JwtSecretsValidator enforces this at prod boot). rand -base64 64 clears it.
JWT_SECRET="$(openssl rand -base64 64 | tr -d '\n')"
JWT_REFRESH_SECRET="$(openssl rand -base64 64 | tr -d '\n')"
UNSUBSCRIBE_SECRET="$(openssl rand -base64 48 | tr -d '\n')"
# BYOK at-rest key MUST decode to exactly 32 bytes (AES-256). rand -base64 32.
API_KEY_ENCRYPTION_KEY="$(openssl rand -base64 32 | tr -d '\n')"

cat <<EOF

# ── App-owned secrets — paste into .env.prod, then redeploy ────────────────
JWT_SECRET=${JWT_SECRET}
JWT_REFRESH_SECRET=${JWT_REFRESH_SECRET}
UNSUBSCRIBE_SECRET=${UNSUBSCRIBE_SECRET}
API_KEY_ENCRYPTION_KEY=${API_KEY_ENCRYPTION_KEY}
# ───────────────────────────────────────────────────────────────────────────

Effects of rotating each:
  JWT_SECRET / JWT_REFRESH_SECRET  → all existing access + refresh tokens become
      invalid; every user is logged out and must sign in again. Harmless, expected.
  UNSUBSCRIBE_SECRET               → any already-sent unsubscribe links stop working
      (new emails carry freshly-signed links). Fine.
  API_KEY_ENCRYPTION_KEY           → *** every stored BYOK key becomes undecryptable ***.
      Only rotate this if no real user keys exist yet, OR you have re-encrypted /
      are prepared to have affected users re-enter their key. Check first:
        docker compose --env-file .env.prod exec -T postgres \\
          psql -U "\$POSTGRES_USER" -d "\$POSTGRES_DB" \\
          -c "SELECT count(*) FROM user_api_keys WHERE validated_at IS NOT NULL;"
      If that count is 0, rotating is free.

Provider-issued secrets — rotate in the dashboard, paste new value, THEN revoke old:
  CLAUDE_API_KEY         → console.anthropic.com  (API keys)
  RESEND_API_KEY         → resend.com/api-keys
  RESEND_WEBHOOK_SECRET  → resend.com webhook settings (re-reveal signing secret)
  NEWS_API_KEY           → your NewsAPI account
  GOOGLE_CLIENT_ID       → console.cloud.google.com (only if Google login is on; no client secret is used)

Datastore passwords (special handling — env change alone is NOT enough):
  POSTGRES_PASSWORD → the password is stored inside the existing DB volume, so you
      must ALTER it, not just change the env:
        docker compose --env-file .env.prod exec -T postgres \\
          psql -U "\$POSTGRES_USER" -d "\$POSTGRES_DB" \\
          -c "ALTER USER \$POSTGRES_USER WITH PASSWORD 'NEW_PASSWORD';"
      then set POSTGRES_PASSWORD=NEW_PASSWORD in .env.prod and restart the stack.
  REDIS_PASSWORD → read from env at container start; change it in .env.prod and
      restart (both redis and backend pick it up together). No ALTER needed.

After editing .env.prod, redeploy:  ./scripts/deploy-prod.sh   (add --cloudflare only on a Cloudflare-origin box)
EOF
