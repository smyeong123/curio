import http from 'k6/http'
import { check } from 'k6'

const BASE = __ENV.BASE || 'http://localhost:8080'
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN
const QUIZ_ID = __ENV.QUIZ_ID
if (!ACCESS_TOKEN || !QUIZ_ID) {
  throw new Error('ACCESS_TOKEN and QUIZ_ID env vars are required.')
}

export const options = {
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<250']
  }
}

// QuizSubmitRequest takes a map of question id (1-5) → chosen option, e.g.
// {"1":"A","2":"A",...} — not an array of {id, answer} (that 400s).
const answers = Object.fromEntries(Array.from({ length: 5 }, (_, i) => [String(i + 1), 'A']))

export default function () {
  const res = http.post(
    `${BASE}/api/v1/quiz/${QUIZ_ID}/submit`,
    JSON.stringify({ answers }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${ACCESS_TOKEN}`
      }
    }
  )
  check(res, {
    // Resubmission is idempotent since V25 ("better score wins") — always 200.
    'status 200': (r) => r.status === 200,
    'no 5xx': (r) => r.status < 500
  })
}
