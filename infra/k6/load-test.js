
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const deniedRate    = new Rate('denied_rate');
const allowedCount  = new Counter('allowed_count');
const deniedCount   = new Counter('denied_count');

export const options = {
  stages: [
    { duration: '20s', target: 20  },
    { duration: '40s', target: 50  },
    { duration: '20s', target: 0   },
  ],
  thresholds: {
    http_req_duration: ['p(95)<150'],
    http_req_failed:   ['rate<0.01'],
  },
};

export default function () {
  const payload = JSON.stringify({
    clientKey: `user:vu-${__VU}`,
    endpoint:  '/api/products',
    algorithm: 'TOKEN_BUCKET',
  });

  const res = http.post('http://localhost:8080/api/v1/ratelimit/check', payload, {
    headers: { 'Content-Type': 'application/json' },
  });

  const body = JSON.parse(res.body);

  check(res, {
    'valid status':       (r) => r.status === 200 || r.status === 429,
    'has allowed field':  (r) => body.hasOwnProperty('allowed'),
    'latency ok':         (r) => r.timings.duration < 150,
  });

  if (body.allowed) allowedCount.add(1);
  else              deniedCount.add(1);

  deniedRate.add(!body.allowed);
}