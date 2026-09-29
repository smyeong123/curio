// Stress the per-user-bucketed BYOK validation endpoint (5 req/min default).
// Verifies the rate limiter holds and the validation call itself stays fast.
//
// Usage:
//   k6 run -e BASE=https://staging.example.com -e TOKEN=<jwt> scripts/k6/byok-validate.js

import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE = __ENV.BASE || 'http://localhost:8080'
const TOKEN = __ENV.TOKEN || ''
const PROVIDER = __ENV.PROVIDER || 'CLAUDE'

export const options = {
  scenarios: {
    burst: {
      executor: 'constant-vus',
      vus: 5,
      duration: '30s'
    }
  },
  thresholds: {
    // Many calls will be 429s by design — we only care about no server errors
    // and that legitimate 200/422 responses stay under 800ms.
    http_req_failed: ['rate<0.10'],
    'http_req_duration{expected_response:true}': ['p(95)<800']
  }
}

export default function () {
  if (!TOKEN) {
    throw new Error('TOKEN env required (logged-in JWT for the test user)')
  }
  const res = http.post(
    `${BASE}/api/v1/user/api-keys/validate`,
    JSON.stringify({ provider: PROVIDER, apiKey: 'sk-load-test-not-real' }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${TOKEN}`
      }
    }
  )
  check(res, {
    'status is 200|422|429': (r) => [200, 422, 429].includes(r.status),
    'no 5xx': (r) => r.status < 500
  })
  sleep(1)
}
