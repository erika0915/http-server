import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TARGET_VUS = Number(__ENV.TARGET_VUS || 20);
const RAMP_UP = __ENV.RAMP_UP || '30s';
const STEADY = __ENV.STEADY || '1m';
const RAMP_DOWN = __ENV.RAMP_DOWN || '30s';
const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || 300);
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 1);

export const options = {
  stages: [
    { duration: RAMP_UP, target: TARGET_VUS },
    { duration: STEADY, target: TARGET_VUS },
    { duration: RAMP_DOWN, target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: [`p(95)<${P95_THRESHOLD_MS}`],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
  const responses = http.batch([
    {
      method: 'GET',
      url: `${BASE_URL}/`,
      params: { tags: { name: 'GET /' } },
    },
    {
      method: 'GET',
      url: `${BASE_URL}/style.css`,
      params: { tags: { name: 'GET /style.css' } },
    },
    {
      method: 'GET',
      url: `${BASE_URL}/app.js`,
      params: { tags: { name: 'GET /app.js' } },
    },
  ]);

  check(responses[0], {
    'index.html status is 200': (response) => response.status === 200,
  });

  check(responses[1], {
    'style.css status is 200': (response) => response.status === 200,
  });

  check(responses[2], {
    'app.js status is 200': (response) => response.status === 200,
  });

  sleep(SLEEP_SECONDS);
}
