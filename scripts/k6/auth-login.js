// WARNING: /auth/login is two-step now — a VALID password does not return a
// session; it emails a real 2FA code via Resend on every successful request.
// Point EMAIL at a throwaway account or run the target with
// AUTH_EMAIL_VERIFICATION_ENABLED=false, or you will spam real email.
// Also: RateLimitingFilter buckets /login at 5 req/min per client IP, so all
// VUs from one machine share one bucket — this script measures the rate
// limiter's behavior under burst (expect mostly 429s), not raw login latency.
import http from 'k6/http'
import { check } from 'k6'

const BASE = __ENV.BASE || 'http://localhost:8080'
const EMAIL = __ENV.EMAIL || 'loadtest@example.com'
const PASSWORD = __ENV.PASSWORD || 'CorrectHorseBatteryStaple!'

export const options = {
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<400']
  }
}

export default function () {
  const res = http.post(
    `${BASE}/api/v1/auth/login`,
    JSON.stringify({ email: EMAIL, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } }
  )
  check(res, {
    'status is 200, 401 or 429': (r) =>
      r.status === 200 || r.status === 401 || r.status === 429,
    'no 5xx': (r) => r.status < 500
  })
}
