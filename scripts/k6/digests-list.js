import http from 'k6/http'
import { check } from 'k6'

const BASE = __ENV.BASE || 'http://localhost:8080'
const ACCESS_TOKEN = __ENV.ACCESS_TOKEN
if (!ACCESS_TOKEN) {
  throw new Error('ACCESS_TOKEN env var is required — log in first and copy the JWT.')
}

export const options = {
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<300']
  }
}

export default function () {
  const res = http.get(`${BASE}/api/v1/news/digests?page=0&size=20`, {
    headers: { Authorization: `Bearer ${ACCESS_TOKEN}` }
  })
  check(res, {
    'status 200': (r) => r.status === 200,
    'has body': (r) => r.body.length > 0
  })
}
