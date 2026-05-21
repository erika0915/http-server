# Benchmark Report

검증과 성능 개선 4번은 실제 부하 테스트 결과를 기록하고 분석하는 단계입니다.

## 목표

worker 수에 따라 서버 처리량과 지연 시간이 어떻게 달라지는지 측정합니다.

Nginx와 동일한 환경에서 비교해 Java NIO로 직접 구현한 서버의 성능 수준을 확인합니다.

## 측정 환경

```text
측정 머신   : MacBook Pro (Apple Silicon, 10코어)
서버 실행   : 로컬 (localhost:8080)
부하 도구   : k6 v1.4.2 (서버와 동일한 머신에서 실행)
모니터링    : Prometheus + Grafana (Docker)
```

로컬 환경이므로 k6와 서버가 같은 CPU를 공유합니다.
절대 수치보다 조건별 상대적인 변화에 의미가 있습니다.

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

### worker 수별 비교 (내 서버)

| workers | RPS      | p95 latency | p99 latency | error rate |
|---------|----------|-------------|-------------|------------|
| 1       | 37,995/s | 9.48ms      | 11.49ms     | 0%         |
| 2       | 59,579/s | 5.34ms      | 6.95ms      | 0%         |
| 4       | 72,408/s | 4.70ms      | 6.15ms      | 0%         |
| 10      | 67,783/s | 5.00ms      | 8.06ms      | 0%         |

### Nginx 비교

| 서버 | workers | RPS      | p95 latency | error rate |
|------|---------|----------|-------------|------------|
| 내 서버 | 4    | 72,408/s | 4.70ms      | 0%         |
| Nginx   | 1    | 56,985/s | 6.11ms      | 0%         |
| Nginx   | 4    | 84,850/s | 4.19ms      | 0%         |
| Nginx   | auto(10) | 87,470/s | 3.03ms   | 0%         |

## 분석

### worker 수에 따른 성능 변화

worker 1 → 2 구간에서 RPS가 37,995에서 59,579로 57% 증가했습니다.
단일 Worker가 모든 연결을 혼자 처리하다가 2개로 분산되면서 Multi EventLoop의 효과가 가장 뚜렷하게 나타납니다.

worker 2 → 4 구간에서는 21% 추가 증가했습니다.
개선은 계속되지만 증가폭이 줄었습니다.

worker 4 → 10 구간에서 RPS가 72,408에서 67,783으로 오히려 6% 감소했습니다.
k6와 서버가 같은 10코어를 공유하는 환경에서 스레드 수가 사용 가능한 코어 수를 초과하면
context switching 비용이 늘어 성능이 떨어집니다.
이 환경에서는 **workers=4가 최적점**이었습니다.

### Nginx 비교

내 서버 workers=4 기준으로 Nginx auto(10) 대비 약 **82% 수준**의 처리량을 달성했습니다.

```text
내 서버 workers=4  → 72,408 RPS
Nginx auto(10)     → 87,470 RPS
비율               → 82.8%
```

내 서버가 Nginx workers=1(56,985 RPS)보다 27% 빠른 점도 주목할 만합니다.
worker 수 튜닝만으로 기본 설정 Nginx를 앞선 결과입니다.

Nginx workers=4 → auto(10) 구간에서 고작 3% 증가에 그쳤습니다.
Nginx는 C로 작성되어 스레드 오버헤드가 적어 workers=4에서 이미 거의 최대치에 도달했습니다.
반면 내 서버는 workers=4 → 10 구간에서 JVM 오버헤드로 인해 성능이 하락했습니다.

### sweet spot이 존재하는 이유

```text
workers 증가 → 병렬 처리 가능한 연결 수 증가 → RPS 상승
workers 증가 → 스레드 수 증가 → context switching 비용 증가

두 효과가 상쇄되는 지점이 sweet spot
이 환경에서는 workers=4
```

Netty도 같은 이유로 기본 worker 수를 CPU 코어 수 * 2로 잡습니다.
실제 서버 환경에서는 k6가 없어 서버 전용 코어가 보장되므로
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
sweet spot이 어디인지 확인했다.
Nginx와 동일 환경에서 성능을 비교했다.
모든 측정에서 error rate 0%를 유지했다.
```
