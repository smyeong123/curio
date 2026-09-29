# k6 load tests for Curio

Install k6: https://k6.io/docs/get-started/installation/

Set `BASE` to your target host (default `http://localhost:8080`). All scripts
accept the same `BASE` env variable.

```bash
# Auth login burst: exercises the 5/min-per-IP login rate limiter (expect
# mostly 429s past the first 5 requests). CAUTION: with a valid EMAIL/PASSWORD
# each accepted login emails a real 2FA code — use a throwaway account or set
# AUTH_EMAIL_VERIFICATION_ENABLED=false on the target.
k6 run --vus 50 --duration 2m -e BASE=http://localhost:8080 scripts/k6/auth-login.js

# Digest list, authenticated.
k6 run --vus 25 --duration 2m \
  -e BASE=http://localhost:8080 \
  -e ACCESS_TOKEN=<jwt> \
  scripts/k6/digests-list.js

# Quiz submit.
k6 run --vus 10 --duration 2m \
  -e BASE=http://localhost:8080 \
  -e ACCESS_TOKEN=<jwt> \
  -e QUIZ_ID=<quiz-uuid> \
  scripts/k6/quiz-submit.js

# BYOK validate — exercises per-user rate limiter (expects mix of 200/422/429).
k6 run -e BASE=http://localhost:8080 -e TOKEN=<jwt> scripts/k6/byok-validate.js
```

### Thresholds

Each script defines pass/fail thresholds:

- `auth-login.js` — p95 < 400 ms
- `digests-list.js` — p95 < 300 ms
- `quiz-submit.js` — p95 < 250 ms
- `byok-validate.js` — p95 < 800 ms on non-rate-limited responses; mix of 200/422/429 expected

Failing thresholds exit non-zero, suitable for CI gating.
