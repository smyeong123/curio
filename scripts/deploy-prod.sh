#!/usr/bin/env bash
#
# Bring up the full production stack WITH TLS termination (Caddy + Let's Encrypt).
# This is the correct production entrypoint — plain `docker compose up -d` serves
# the app over HTTP on :80 only, which means JWTs and passwords travel in clear.
#
# The production box (t3.micro, 1 GB RAM) CANNOT build images — the Maven/Vite
# build OOMs (see docs/DEPLOY.md). Images are built on a dev machine and
# pushed to Docker Hub, so the default flow here is PULL + UP (no build).
#
# Prerequisites (one-time):
#   1. A domain whose A/AAAA record points at this host.
#   2. CURIO_DOMAIN and ACME_EMAIL set in .env.prod.
#   3. Ports 80 AND 443 reachable from the public internet (security group / firewall).
#   4. The tagged images referenced by .env.prod already pushed to the registry.
#
# Usage:
#   ./scripts/deploy-prod.sh            # pull pre-built images, then up (production)
#   ./scripts/deploy-prod.sh --build    # build locally instead of pulling (dev machine ONLY)
set -euo pipefail
cd "$(dirname "$0")/.."

BUILD=0
# TLS_MODE: "caddy" (default — Caddy + Let's Encrypt) or "cloudflare" (nginx
# terminates with a Cloudflare Origin Cert; use when the origin is behind
# Cloudflare). Select with --cloudflare or TLS_MODE=cloudflare.
TLS_MODE="${TLS_MODE:-caddy}"
for arg in "$@"; do
  case "$arg" in
    --build)
      BUILD=1
      echo "WARNING: --build builds images locally. Do NOT run this on the 1 GB prod box" >&2
      echo "         (the Maven/Vite build OOMs there). Build on a dev machine and push." >&2
      ;;
    --cloudflare) TLS_MODE="cloudflare" ;;
    --caddy)      TLS_MODE="caddy" ;;
  esac
done

ENV_FILE="${ENV_FILE:-.env.prod}"
[[ -f "$ENV_FILE" ]] || { echo "missing $ENV_FILE" >&2; exit 1; }

# shellcheck disable=SC1090
set -a; source "$ENV_FILE"; set +a
: "${CURIO_DOMAIN:?set CURIO_DOMAIN in $ENV_FILE, e.g. curio-news.dev, required for TLS}"

if [[ "$TLS_MODE" == "cloudflare" ]]; then
  : "${TLS_CERT_DIR:?set TLS_CERT_DIR in $ENV_FILE to the host dir holding origin.pem/origin.key}"
  [[ -f "$TLS_CERT_DIR/origin.pem" && -f "$TLS_CERT_DIR/origin.key" ]] \
    || { echo "missing $TLS_CERT_DIR/origin.pem or origin.key (Cloudflare Origin Cert)" >&2; exit 1; }
  TLS_OVERLAY="docker-compose.cf-tls.yml"
else
  : "${ACME_EMAIL:?set ACME_EMAIL in $ENV_FILE for Lets Encrypt notifications}"
  TLS_OVERLAY="docker-compose.tls.yml"
fi

COMPOSE=(docker compose --env-file "$ENV_FILE" -f docker-compose.yml -f "$TLS_OVERLAY")

echo "Deploying with TLS ($TLS_MODE) for https://${CURIO_DOMAIN}"
if [[ "$BUILD" -eq 1 ]]; then
  "${COMPOSE[@]}" up -d --build
else
  "${COMPOSE[@]}" pull
  "${COMPOSE[@]}" up -d
fi

if [[ "$TLS_MODE" == "cloudflare" ]]; then
  echo "Done. nginx is terminating TLS with the Cloudflare Origin Cert."
  echo "Ensure Cloudflare SSL mode is 'Full (strict)' and :80/:443 are firewalled to Cloudflare IPs."
else
  echo "Done. Caddy will obtain/renew the certificate automatically."
fi
echo "Verify:  curl -I https://${CURIO_DOMAIN}"
