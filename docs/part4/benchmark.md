# Benchmark

Step 14는 서버가 단순히 "동작한다"에서 끝나지 않고, 부하를 걸고 지표를 관찰할 수 있는 환경을 만드는 단계입니다.

관련 파일:

```text
src/main/java/httpserver/nio/http/metrics/ServerMetrics.java
src/main/java/httpserver/nio/http/metrics/WorkerStats.java
src/main/java/httpserver/nio/http/metrics/MetricsHandler.java
src/main/java/httpserver/nio/http/metrics/MetricsReporter.java
benchmark/k6/load-test.js
benchmark/docker-compose.yml
benchmark/prometheus/prometheus.yml
benchmark/grafana/provisioning/datasources/datasource.yml
benchmark/grafana/provisioning/dashboards/dashboard.yml
benchmark/grafana/dashboards/nio-http-server-dashboard.json
```

## 목표

이번 단계의 목표는 성능을 "감"으로 판단하지 않고 숫자로 관찰하는 것입니다.

확인할 수 있는 항목:

```text
요청 수
초당 요청 수
active connection
전체 connection 수
read/write byte
worker별 request count
평균 response time
최대 response time
```

## 하위 단계

Step 14는 커밋 가능한 단위로 나누었습니다.

```text
Step 14-1: Java 서버 /metrics 엔드포인트 추가
Step 14-2: k6 단독 부하 테스트 스크립트 작성
Step 14-3: Docker Compose 기반 Prometheus + Grafana 연동
Step 14-4: benchmark 문서 작성
```

## Step 14-1: /metrics

Java 서버가 Prometheus text format으로 내부 지표를 노출합니다.

```text
GET /metrics
Content-Type: text/plain; version=0.0.4
```

노출하는 지표:

```text
nio_http_total_requests
nio_http_active_connections
nio_http_total_connections
nio_http_bytes_read
nio_http_bytes_written
nio_http_uptime_seconds
nio_http_requests_per_second
nio_http_response_time_average_millis
nio_http_response_time_max_millis
nio_http_worker_requests
nio_http_worker_active_connections
```

실행:

```bash
java -cp build/classes/java/main httpserver.nio.http.HttpServer 4
```

검증:

```bash
curl http://localhost:8080/
curl http://localhost:8080/metrics
```

완료 기준:

```text
/metrics가 정상 응답한다
일반 요청 후 nio_http_total_requests가 증가한다
connection 수와 worker별 request count를 확인할 수 있다
```

## Step 14-2: k6

k6로 Docker 없이도 서버에 부하를 걸 수 있습니다.

시나리오:

```text
GET /
GET /style.css
GET /app.js
```

부하 패턴:

```text
30초 ramp-up
1분 steady
30초 ramp-down
```

k6 설치:

```bash
brew install k6
```

실행:

```bash
k6 run benchmark/k6/load-test.js
```

다른 서버 주소로 실행:

```bash
BASE_URL=http://localhost:8080 k6 run benchmark/k6/load-test.js
```

검증:

```text
http_reqs
http_req_duration
p95 latency
http_req_failed
checks
```

완료 기준:

```text
k6 테스트가 정상 실행된다
RPS, p95 latency, error rate를 확인할 수 있다
기본 threshold를 통과하거나 실패 이유를 설명할 수 있다
```

기본 threshold:

```text
http_req_failed < 1%
http_req_duration p95 < 300ms
```

## Step 14-3: Prometheus + Grafana

Prometheus는 Java 서버의 `/metrics`를 주기적으로 수집합니다.

Grafana는 Prometheus에 쌓인 지표를 dashboard로 보여줍니다.

실행 순서:

```bash
java -cp build/classes/java/main httpserver.nio.http.HttpServer 4
docker compose -f benchmark/docker-compose.yml up -d
```

k6를 Docker Compose profile로 실행:

```bash
docker compose -f benchmark/docker-compose.yml --profile load up k6
```

접속:

```text
Prometheus: http://localhost:9090
Grafana:    http://localhost:3000
Login:      admin / admin
```

Prometheus에서 확인할 query:

```text
nio_http_total_requests
nio_http_active_connections
rate(nio_http_total_requests[1m])
```

Grafana dashboard에서 확인할 항목:

```text
RPS
active connections
total requests
bytes read/write
worker별 request count
uptime
response time
```

완료 기준:

```text
Prometheus에서 nio_http_total_requests를 검색할 수 있다
Grafana dashboard에서 서버 지표를 확인할 수 있다
```

## 주요 개념

RPS는 requests per second입니다.

서버가 1초 동안 처리한 요청 수를 의미합니다.

latency는 요청 하나가 응답을 받기까지 걸린 시간입니다.

p95 latency는 전체 요청 중 95%가 이 시간 안에 끝났다는 의미입니다.

p99 latency는 전체 요청 중 99%가 이 시간 안에 끝났다는 의미입니다.

error rate는 실패한 요청 비율입니다.

VU는 k6의 virtual user입니다.

ramp-up은 부하를 점진적으로 올리는 구간입니다.

steady는 일정한 부하를 유지하는 구간입니다.

ramp-down은 부하를 점진적으로 낮추는 구간입니다.

## 결과 해석

RPS가 높아도 p95 latency가 급격히 증가하면 서버가 한계에 가까워졌다는 신호입니다.

error rate가 증가하면 연결 처리, timeout, header parsing, static file response 중 어디에서 실패하는지 서버 로그와 Grafana 지표를 함께 봅니다.

active connection이 부하 종료 후 계속 남아 있으면 connection cleanup 또는 idle timeout을 확인해야 합니다.

worker 수를 늘렸는데 성능이 좋아지지 않을 수 있습니다.

worker가 너무 많으면 context switching 비용이 늘 수 있기 때문입니다.

## Step 12와 Step 13 비교

Step 12는 single selector 구조입니다.

```text
Thread 1개
Selector 1개
accept/read/write 모두 처리
```

Step 13은 multi eventloop 구조입니다.

```text
Boss thread
-> accept 전용

Worker thread
-> read/write 전용
```

같은 k6 시나리오, 같은 public 파일, 같은 worker 수 조건에서 비교해야 의미가 있습니다.

관찰 포인트:

```text
worker 수 증가에 따른 RPS 변화
keep-alive ON/OFF 차이
작은 파일과 큰 파일 응답 차이
부하 종료 후 active connection 정리 여부
```

## 성능 목표 예시

아직 실제 RPS 수치를 확정하지 않습니다.

측정하지 않은 숫자를 문서에 확정적으로 적으면 안 됩니다.

예시 목표:

```text
error rate < 1%
p95 latency < 300ms
5분 이상 부하 테스트에서 crash 0회
부하 종료 후 active connection 누수 없음
```

측정 결과는 직접 테스트한 뒤 채웁니다.

```text
date:
worker count:
scenario:
RPS:
p95 latency:
p99 latency:
error rate:
notes:
```

## 큰 파일 테스트

큰 파일 응답을 테스트하려면 `public` 디렉토리에 샘플 파일을 만듭니다.

```bash
dd if=/dev/zero of=public/big-file.bin bs=1m count=10
```

테스트:

```bash
curl -v http://localhost:8080/big-file.bin
```

현재 단계에서 하지 않는 것:

```text
Buffer Pool
Zero-copy
gzip compression
advanced caching
HTTP/2
```
