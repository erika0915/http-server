# Benchmark Environment

검증과 성능 개선 3번은 서버 성능을 관찰하기 위한 benchmark 환경을 준비하는 단계입니다.

## 목표

서버가 단순히 동작하는지에서 멈추지 않고, 부하가 들어왔을 때 요청 처리량과 연결 상태가 어떻게 변하는지 확인할 수 있게 만듭니다.

이번 단계에서는 세 가지 도구를 사용합니다.

```text
k6         -> HTTP 부하 생성
Prometheus -> 서버 /metrics 수집
Grafana    -> 수집된 지표 시각화
```

## 수정/추가 파일

```text
benchmark/k6/load-test.js
benchmark/docker-compose.yml
benchmark/prometheus/prometheus.yml
benchmark/grafana/provisioning/datasources/datasource.yml
benchmark/grafana/provisioning/dashboards/dashboard.yml
benchmark/grafana/dashboards/nio-http-server-dashboard.json
docs/verification/03-benchmark-environment.md
README.md
```

## 구현 내용

`benchmark/k6/load-test.js`는 정적 파일 서버의 기본 요청 흐름을 부하 테스트합니다.

```text
GET /
GET /style.css
GET /app.js
```

브라우저가 HTML을 받은 뒤 CSS와 JS를 추가로 요청하는 흐름을 k6에서 반복하는 방식입니다.

부하 패턴은 다음 세 단계로 구성했습니다.

```text
ramp-up   -> 가상 사용자를 서서히 증가
steady    -> 일정 부하 유지
ramp-down -> 가상 사용자를 서서히 감소
```

기본값은 다음과 같습니다.

```text
TARGET_VUS=20
RAMP_UP=30s
STEADY=1m
RAMP_DOWN=30s
P95_THRESHOLD_MS=300
```

Prometheus는 Java 서버의 `/metrics` 엔드포인트를 5초마다 수집합니다.

Grafana는 Prometheus datasource와 NIO HTTP Server dashboard를 자동으로 로딩합니다.

## 실행 방법

먼저 Java 서버를 실행합니다.

성능 측정 중에는 요청 단위 debug 로그가 결과에 영향을 줄 수 있으므로 로그를 줄여 실행하는 것이 좋습니다.

```bash
java \
  -Dhttpserver.logLevel=INFO \
  -Dhttpserver.metricsLog=false \
  -cp build/classes/java/main \
  httpserver.nio.http.HttpServer
```

worker 수를 직접 지정하려면 마지막에 숫자를 전달합니다.

```bash
java \
  -Dhttpserver.logLevel=INFO \
  -Dhttpserver.metricsLog=false \
  -cp build/classes/java/main \
  httpserver.nio.http.HttpServer 4
```

## k6 단독 실행

k6가 로컬에 설치되어 있다면 다음처럼 실행합니다.

```bash
k6 run benchmark/k6/load-test.js
```

부하를 조절하려면 환경 변수를 사용합니다.

```bash
TARGET_VUS=50 \
RAMP_UP=30s \
STEADY=2m \
RAMP_DOWN=30s \
P95_THRESHOLD_MS=300 \
k6 run benchmark/k6/load-test.js
```

## Docker Compose 실행

Prometheus와 Grafana를 실행합니다.

```bash
docker compose -f benchmark/docker-compose.yml up -d prometheus grafana
```

접속 주소는 다음과 같습니다.

```text
Prometheus: http://localhost:9090
Grafana:    http://localhost:3000
```

Grafana 기본 계정은 다음과 같습니다.

```text
id: admin
pw: admin
```

Docker로 k6를 실행하려면 `load` profile을 사용합니다.

```bash
docker compose -f benchmark/docker-compose.yml --profile load run --rm k6
```

## 검증 방법

서버 metrics가 노출되는지 확인합니다.

```bash
curl http://localhost:8080/metrics
```

다음 metric이 보이면 정상입니다.

```text
nio_http_total_requests
nio_http_active_connections
nio_http_total_connections
nio_http_bytes_read
nio_http_bytes_written
nio_http_uptime_seconds
nio_http_worker_requests
```

Prometheus에서 다음 query를 검색합니다.

```text
nio_http_total_requests
rate(nio_http_total_requests[1m])
nio_http_active_connections
nio_http_worker_requests
```

Grafana에서는 `NIO HTTP Server` dashboard를 열어 다음 항목을 확인합니다.

```text
Requests Per Second
Active Connections
Total Requests
Network Bytes
Worker Request Count
Response Time
Uptime
```

## 주요 지표

`RPS`는 초당 처리한 요청 수입니다.

`latency`는 요청을 보낸 뒤 응답을 받기까지 걸린 시간입니다.

`p95 latency`는 전체 요청 중 95%가 이 시간 안에 끝났다는 뜻입니다.

`error rate`는 실패한 요청 비율입니다.

`active connections`는 현재 살아 있는 TCP 연결 수입니다.

`worker별 request count`는 Multi EventLoop에서 요청이 여러 worker에 분산되는지 확인하는 데 사용합니다.

## 완료 기준

```text
k6 load-test.js가 실행된다.
/metrics에서 Prometheus text format 지표를 확인할 수 있다.
Prometheus가 nio_http_* 지표를 수집한다.
Grafana dashboard에서 서버 지표를 확인할 수 있다.
README 진행 상황 표가 benchmark 환경 문서로 연결된다.
```

## 다음 단계

다음 단계에서는 실제 측정 결과를 기록합니다.

예를 들어 다음 항목을 비교합니다.

```text
worker 1개 vs worker 4개
작은 정적 파일 vs 큰 정적 파일
keep-alive 연결 수 변화
RPS / p95 latency / error rate
```

실제 측정하지 않은 수치는 문서에 확정적으로 쓰지 않습니다.
