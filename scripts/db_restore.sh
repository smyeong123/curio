#!/usr/bin/env bash
#
# Restore a Curio Postgres dump produced by pg_backup.sh into the running
# container. DESTRUCTIVE: the dump is created with --clean --if-exists, so it
# drops and recreates objects. Requires explicit confirmation.
#
# Usage:
#   ./scripts/db_restore.sh ./backups/curio-curio-20260622T033000Z.sql.gz
#
# Env:
#   PG_CONTAINER   container name      (default: curio-postgres)
#   ENV_FILE       env file            (default: .env.prod)
set -euo pipefail

cd "$(dirname "$0")/.."

DUMP="${1:?usage: db_restore.sh <path-to-backup.sql.gz>}"
[[ -f "$DUMP" ]] || { echo "no such file: $DUMP" >&2; exit 1; }

ENV_FILE="${ENV_FILE:-.env.prod}"
PG_CONTAINER="${PG_CONTAINER:-curio-postgres}"
if [[ -f "$ENV_FILE" ]]; then set -a; source "$ENV_FILE"; set +a; fi
: "${POSTGRES_DB:?POSTGRES_DB not set (check $ENV_FILE)}"
: "${POSTGRES_USER:?POSTGRES_USER not set (check $ENV_FILE)}"

echo "About to restore '$DUMP' into database '$POSTGRES_DB' on '$PG_CONTAINER'."
echo "This OVERWRITES existing data."

# Refuse to restore under a live backend: --clean drops objects out from under
# open connections (app errors, FK violations, wedged restore). Enforce the
# warning instead of just printing it.
BACKEND_CONTAINER="${BACKEND_CONTAINER:-curio-backend}"
if docker ps --format '{{.Names}}' | grep -qx "$BACKEND_CONTAINER"; then
  echo "ERROR: backend container '$BACKEND_CONTAINER' is running." >&2
  echo "Stop it first:  docker compose --env-file $ENV_FILE stop backend" >&2
  exit 1
fi

read -r -p "Type the database name to confirm: " CONFIRM
[[ "$CONFIRM" == "$POSTGRES_DB" ]] || { echo "aborted."; exit 1; }

# ON_ERROR_STOP: psql otherwise continues past errors and exits 0, so a
# truncated dump or mid-restore failure would print "restore complete" over a
# half-restored database. --single-transaction rolls the whole restore back on
# any error instead of leaving it partially applied.
gunzip -c "$DUMP" | docker exec -i "$PG_CONTAINER" \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 --single-transaction
echo "restore complete. Start the backend: docker compose --env-file $ENV_FILE start backend"
