#!/usr/bin/env bash
#
# Dump the Curio Postgres database from the running container to a timestamped,
# gzipped file and prune backups older than the retention window.
#
# Postgres in this stack lives on a *local docker volume* with no managed
# snapshots — losing the host loses the data. Run this on a cron so there is a
# recoverable copy off the volume (ideally synced off-box; see SYNC below).
#
# Usage:
#   ./scripts/pg_backup.sh
#
# Cron (daily 03:30, before the 06:00 digest job touches the DB):
#   30 3 * * *  cd /opt/curio && ./scripts/pg_backup.sh >> /var/log/curio-backup.log 2>&1
#
# Env (override as needed):
#   BACKUP_DIR        where dumps are written        (default: ./backups)
#   RETENTION_DAYS    delete dumps older than this   (default: 14)
#   PG_CONTAINER      container name                 (default: curio-postgres)
#   ENV_FILE          env file with POSTGRES_*       (default: .env.prod)
set -euo pipefail

# Dumps hold user emails, password hashes, token hashes, and encrypted BYOK
# keys — keep the backup dir and files owner-only readable.
umask 077

cd "$(dirname "$0")/.."

ENV_FILE="${ENV_FILE:-.env.prod}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
PG_CONTAINER="${PG_CONTAINER:-curio-postgres}"

# Load POSTGRES_DB / POSTGRES_USER / POSTGRES_PASSWORD from the env file.
if [[ -f "$ENV_FILE" ]]; then
  set -a; source "$ENV_FILE"; set +a
fi
: "${POSTGRES_DB:?POSTGRES_DB not set (check $ENV_FILE)}"
: "${POSTGRES_USER:?POSTGRES_USER not set (check $ENV_FILE)}"

mkdir -p "$BACKUP_DIR"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT="$BACKUP_DIR/curio-${POSTGRES_DB}-${STAMP}.sql.gz"

echo "[$(date -u)] dumping $POSTGRES_DB from $PG_CONTAINER -> $OUT"
# -Fc would be smaller/faster to restore, but plain SQL is the most portable and
# trivially inspectable. pg_dump runs inside the container so no client install
# is required on the host.
docker exec "$PG_CONTAINER" pg_dump -U "$POSTGRES_USER" --no-owner --clean --if-exists "$POSTGRES_DB" \
  | gzip -9 > "$OUT"

# Fail loudly if the dump is suspiciously tiny (e.g. auth failed mid-pipe).
SIZE=$(wc -c < "$OUT")
if [[ "$SIZE" -lt 1024 ]]; then
  echo "ERROR: backup is only ${SIZE} bytes — likely failed. Keeping for inspection." >&2
  exit 1
fi

echo "[$(date -u)] backup OK (${SIZE} bytes). Pruning > ${RETENTION_DAYS} days."
find "$BACKUP_DIR" -name 'curio-*.sql.gz' -type f -mtime +"$RETENTION_DAYS" -print -delete

# SYNC (recommended): copy off-box so a host failure can't take the backups with
# it. Uncomment and configure one of these:
#   aws s3 cp "$OUT" "s3://your-bucket/curio-backups/"
#   rclone copy "$OUT" remote:curio-backups/
echo "[$(date -u)] done."
