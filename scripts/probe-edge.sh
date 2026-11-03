#!/usr/bin/env bash
#
# Probe the public edge (Cloudflare -> nginx -> backend) for intermittent
# failures. Read-only: it only sends GET requests and writes nothing outside a
# temp dir that is removed on exit.
#
# Alternates GET /healthz (nginx + backend) and GET / (nginx static SPA shell),
# logging one line per request:
#   <timestamp> <path> <http_code> <time_total_s> <cf-ray>
# then prints a summary: total, count per status, 5xx+000 failure rate, and
# p50/p95 of time_total. http_code 000 means curl got no HTTP response
# (timeout after 20 s, connection reset, TLS failure).
#
# Usage:
#   scripts/probe-edge.sh [BASE_URL] [N] [INTERVAL_SECONDS]
#   scripts/probe-edge.sh                                # https://curio-news.dev, 30 requests, 1 s apart
#   scripts/probe-edge.sh https://curio-news.dev 120 0.5
#
# Portable to macOS (bash 3.2, BSD sort/awk/date): no associative arrays,
# no GNU-only flags.
set -u

BASE_URL="${1:-https://curio-news.dev}"
N="${2:-30}"
INTERVAL="${3:-1}"
BASE_URL="${BASE_URL%/}"

case "$N" in ''|*[!0-9]*) echo "N must be a positive integer (got '$N')" >&2; exit 2 ;; esac
[ "$N" -ge 1 ] || { echo "N must be >= 1" >&2; exit 2; }
command -v curl >/dev/null 2>&1 || { echo "curl not found" >&2; exit 2; }

TMP_DIR="$(mktemp -d 2>/dev/null || mktemp -d -t probe-edge)"
trap 'rm -rf "$TMP_DIR"' EXIT INT TERM
RESULTS="$TMP_DIR/results"
HDRS="$TMP_DIR/headers"
: > "$RESULTS"

echo "Probing $BASE_URL — $N requests, ${INTERVAL}s apart (alternating /healthz and /)"
printf '%-20s %-9s %-4s %-9s %s\n' "timestamp" "path" "code" "time_s" "cf-ray"

i=1
while [ "$i" -le "$N" ]; do
  if [ $((i % 2)) -eq 1 ]; then path="/healthz"; else path="/"; fi
  : > "$HDRS"
  # -w output is "<code> <time_total>"; curl prints 000 when no response arrived.
  out="$(curl -sS -o /dev/null -D "$HDRS" -m 20 \
           -H 'Cache-Control: no-cache' \
           -w '%{http_code} %{time_total}' \
           "$BASE_URL$path" 2>/dev/null)"
  code="${out%% *}"
  t="${out##* }"
  [ -n "$code" ] || code="000"
  [ -n "$t" ] && [ "$t" != "$out" ] || t="0"
  ray="$(grep -i '^cf-ray:' "$HDRS" 2>/dev/null | tail -n 1 | sed 's/^[^:]*:[[:space:]]*//' | tr -d '\r')"
  [ -n "$ray" ] || ray="-"
  ts="$(date '+%Y-%m-%dT%H:%M:%S')"
  printf '%-20s %-9s %-4s %-9s %s\n' "$ts" "$path" "$code" "$t" "$ray"
  printf '%s %s %s\n' "$path" "$code" "$t" >> "$RESULTS"
  if [ "$i" -lt "$N" ]; then sleep "$INTERVAL"; fi
  i=$((i + 1))
done

echo
echo "Summary"
awk '{ print $2 }' "$RESULTS" | sort | uniq -c | awk '{ printf "  status %s: %d\n", $2, $1 }'
awk '
  { total++; if ($2 == "000" || $2 ~ /^5/) bad++ }
  END {
    printf "  total: %d\n", total
    printf "  5xx+000: %d (%.1f%%)\n", bad, (total ? 100 * bad / total : 0)
  }' "$RESULTS"
awk '{ print $3 }' "$RESULTS" | sort -n | awk '
  { v[NR] = $1 }
  END {
    if (NR == 0) exit
    # Nearest-rank percentiles.
    p50 = int(0.50 * NR + 0.999999); if (p50 < 1) p50 = 1
    p95 = int(0.95 * NR + 0.999999); if (p95 < 1) p95 = 1
    printf "  time_total p50: %.3fs  p95: %.3fs  max: %.3fs\n", v[p50], v[p95], v[NR]
  }'
