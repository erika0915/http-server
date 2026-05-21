# Logging Control

검증과 성능 개선 2번은 서버 로그를 제어하는 단계입니다.

## 목표

학습할 때는 요청/응답 흐름을 자세히 보고, benchmark나 테스트를 실행할 때는 과도한 로그를 줄일 수 있게 만듭니다.

## 구현 내용

공통 로그 유틸리티를 추가했습니다.

```text
src/main/java/httpserver/nio/http/logging/ServerLogger.java
```

서버 실행 시 system property로 로그 레벨을 선택할 수 있습니다.

```text
httpserver.logLevel=DEBUG
httpserver.logLevel=INFO
httpserver.logLevel=OFF
```

기본값은 `DEBUG`입니다.

```text
DEBUG -> 학습용 상세 로그 출력
INFO  -> 서버 시작, metrics 등 주요 로그 중심 출력
OFF   -> stdout/stderr 로그 최소화
```

metrics 주기 출력은 별도 옵션으로 끌 수 있습니다.

```text
httpserver.metricsLog=false
```

## 사용 예시

학습용 기본 실행:

```bash
java -cp build/classes/java/main httpserver.nio.http.HttpServer
```

benchmark용 실행:

```bash
java \
  -Dhttpserver.logLevel=INFO \
  -Dhttpserver.metricsLog=false \
  -cp build/classes/java/main \
  httpserver.nio.http.HttpServer
```

로그를 최대한 끄고 실행:

```bash
java \
  -Dhttpserver.logLevel=OFF \
  -Dhttpserver.metricsLog=false \
  -cp build/classes/java/main \
  httpserver.nio.http.HttpServer
```

## 핵심 개념

서버 구현 단계에서는 로그가 학습에 도움이 됩니다.

예를 들어 다음 로그는 서버 내부 흐름을 이해하는 데 좋습니다.

```text
read bytes
request complete
parsed request
selected route
write bytes
connection state
```

하지만 성능 측정 단계에서는 요청마다 로그를 출력하면 결과에 영향을 줄 수 있습니다.

그래서 benchmark 전에는 요청 단위 debug 로그를 끄는 것이 좋습니다.

## 코드 흐름

```text
System property 읽기
-> ServerLogger log level 결정
-> DEBUG 로그는 DEBUG일 때만 출력
-> INFO 로그는 INFO 또는 DEBUG일 때 출력
-> metrics 로그는 httpserver.metricsLog 옵션으로 제어
```

## 완료 기준

```text
공통 ServerLogger가 존재한다.
메인 서버의 주요 로그가 ServerLogger를 사용한다.
DEBUG/INFO/OFF 로그 레벨을 선택할 수 있다.
metrics 주기 출력을 끌 수 있다.
컴파일과 테스트가 통과한다.
```
