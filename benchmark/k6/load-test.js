import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 20 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<300'],
  },
};

export default function () {
  const responses = http.batch([
    ['GET', `${BASE_URL}/`],
    ['GET', `${BASE_URL}/style.css`],
    ['GET', `${BASE_URL}/app.js`],
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

  sleep(1);
}
