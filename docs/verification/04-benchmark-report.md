# Benchmark Report

검증과 성능 개선 4번은 실제 부하 테스트 결과를 기록하고 분석하는 단계입니다.

## 목표

worker 수에 따라 서버 처리량과 지연 시간이 어떻게 달라지는지 측정합니다.

Multi EventLoop 구조에서 worker를 늘리는 것이 실제로 성능에 영향을 주는지,
그리고 어느 시점부터 효과가 줄어드는지 확인합니다.

## 측정 환경

```text
측정 머신   : MacBook Pro (Apple Silicon, 10코어)
서버 실행   : 로컬 (localhost:8080)
부하 도구   : k6 v1.4.2 (서버와 동일한 머신에서 실행)
모니터링    : Prometheus + Grafana (Docker)
```

로컬 환경이므로 k6와 서버가 같은 CPU를 공유합니다.
절대 수치보다 worker 수에 따른 상대적인 변화에 의미가 있습니다.

## 부하 설정

```text
가상 사용자 : 100 VUs
ramp-up    : 30s
steady     : 1m
ramp-down  : 30s
sleep      : 없음 (VU가 쉬지 않고 요청)
요청 패턴  : GET /, GET /style.css, GET /app.js (배치 요청)
```

## 측정 결과

| workers | RPS      | p95 latency | p99 latency | error rate |
|---------|----------|-------------|-------------|------------|
| 1       | 37,995/s | 9.48ms      | 11.49ms     | 0%         |
| 2       | 59,579/s | 5.34ms      | 6.95ms      | 0%         |
| 4       | 72,408/s | 4.70ms      | 6.15ms      | 0%         |
| 10      | 67,783/s | 5.00ms      | 8.06ms      | 0%         |

모든 측정에서 임계값(p95 < 300ms, error rate < 1%)을 충족했습니다.

## 분석

### worker 1 → 2: 가장 큰 폭의 개선

RPS가 37,995에서 59,579로 약 57% 증가했습니다.
p95 latency도 9.48ms에서 5.34ms로 절반 가까이 줄었습니다.

단일 Worker는 모든 연결의 읽기/쓰기를 혼자 처리합니다.
Worker가 2개로 늘어나면 연결이 두 스레드로 분산되어 병렬 처리가 시작됩니다.
이 구간에서 Multi EventLoop 구조의 효과가 가장 뚜렷하게 나타납니다.

### worker 2 → 4: 추가 개선, 증가폭은 감소

RPS가 59,579에서 72,408로 약 21% 증가했습니다.
p95 latency도 5.34ms에서 4.70ms로 개선됐습니다.

개선은 계속되지만 worker 1 → 2 구간보다 증가폭이 줄었습니다.

### worker 4 → 10: 성능 정체 및 소폭 하락

RPS가 72,408에서 67,783으로 오히려 약 6% 감소했습니다.
p95 latency도 4.70ms에서 5.00ms로 소폭 증가했습니다.

10코어 머신에서 서버 스레드 10개를 띄우면 k6, JVM, OS 등이 함께 CPU를 경쟁합니다.
worker 수가 사용 가능한 코어 수를 초과하면 context switching 비용이 늘어
오히려 성능이 떨어집니다.

이 환경에서는 **workers=4가 최적점**이었습니다.

### sweet spot이 존재하는 이유

```text
workers 증가 → 병렬 처리 가능한 연결 수 증가 → RPS 상승
workers 증가 → 스레드 수 증가 → context switching 비용 증가

두 효과가 상쇄되는 지점이 sweet spot
이 환경에서는 workers=4
```

실제 서버 환경에서는 서버 전용 코어가 보장되므로
sweet spot이 더 높은 worker 수에서 형성될 수 있습니다.

## 한계

```text
k6와 서버가 같은 머신에서 실행되어 CPU 경합이 발생합니다.
측정마다 MacBook 발열 상태에 따라 수치가 달라질 수 있습니다.
절대 수치(RPS)는 전용 서버 환경 대비 낮게 나옵니다.
```

## 완료 기준

```text
worker 수별 RPS와 latency를 측정했다.
worker 수가 늘수록 성능이 어떻게 변하는지 확인했다.
sweet spot이 어디인지 확인했다.
모든 측정에서 error rate 0%를 유지했다.
```
