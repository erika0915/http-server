# Multi EventLoop

Step 13은 서버 구조를 하나의 Selector에서 Boss/Worker EventLoop 구조로 분리하는 단계입니다.

관련 파일:

```text
src/main/java/httpserver/nio/http/HttpServer.java
src/main/java/httpserver/nio/http/eventloop/BossEventLoop.java
src/main/java/httpserver/nio/http/eventloop/WorkerEventLoop.java
src/main/java/httpserver/nio/http/eventloop/EventLoopGroup.java
src/main/java/httpserver/nio/http/connection/Connection.java
```

## 이전 구조

이전 서버는 하나의 thread와 하나의 Selector가 모든 일을 처리했습니다.

```text
Selector 1개
-> accept
-> read
-> write
-> timeout cleanup
```

학습용으로는 단순하지만, 모든 연결의 accept/read/write가 한 곳에 모이면 CPU core를 여러 개 활용하기 어렵습니다.

## 새로운 구조

이번 단계에서는 Boss와 Worker를 분리합니다.

```text
Client
-> Boss EventLoop
-> accept
-> Worker EventLoop #1
-> Worker EventLoop #2
-> Worker EventLoop #3
```

Boss Thread:

```text
OP_ACCEPT 전용
```

Worker Thread:

```text
OP_READ / OP_WRITE 전용
Connection lifecycle 관리
Timeout cleanup 수행
```

## Reactor Pattern

Reactor Pattern은 I/O 이벤트가 준비되면 EventLoop가 해당 이벤트를 받아 적절한 handler로 넘기는 구조입니다.

이번 서버에서는 다음처럼 나뉩니다.

```text
BossEventLoop
-> accept event 처리
-> WorkerEventLoop에 SocketChannel 전달

WorkerEventLoop
-> read event 처리
-> HTTP request parse
-> route
-> write event 처리
```

## 왜 accept와 read/write를 나누는가?

accept는 새 TCP 연결을 받는 일입니다.

read/write는 이미 연결된 클라이언트의 데이터를 처리하는 일입니다.

두 역할을 나누면 새 연결 수락과 실제 요청 처리를 분리할 수 있고, 여러 Worker가 각자 Selector를 가지고 병렬로 connection을 처리할 수 있습니다.

## connection은 왜 하나의 worker에 고정되는가?

`Connection`은 `readBuffer`, `writeBuffer`, 상태 머신, keep-alive 상태를 가지고 있습니다.

이 객체를 여러 worker가 동시에 만지면 동기화 문제가 생깁니다.

그래서 한 connection은 처음 배정된 worker에 고정됩니다. migration은 하지 않습니다.

## round-robin 분산

Boss는 새 connection을 받을 때마다 다음 Worker를 선택합니다.

```text
conn-1 -> worker-1
conn-2 -> worker-2
conn-3 -> worker-1
conn-4 -> worker-2
```

## wakeup()이 필요한 이유

Worker는 `selector.select(...)` 안에서 잠들어 있을 수 있습니다.

Boss thread가 Worker에게 새 SocketChannel 등록 요청을 queue에 넣어도 Worker가 잠든 상태라면 바로 처리하지 못합니다.

그래서 `selector.wakeup()`을 호출해 Worker를 깨우고, Worker thread 안에서 안전하게 `register(...)`를 수행합니다.

## Netty / NGINX와의 관계

Netty도 BossGroup / WorkerGroup 구조를 사용합니다.

BossGroup은 accept를 담당하고, WorkerGroup은 read/write를 담당합니다.

NGINX도 여러 worker process를 통해 CPU core를 활용합니다.

이번 단계는 그 구조의 아주 작은 학습용 버전입니다.

## 실행

기본 worker 수는 사용 가능한 CPU core 수를 기반으로 정합니다.

직접 worker 수를 지정하려면 main argument로 숫자를 넘깁니다.

```bash
java -cp build/classes/java/main httpserver.nio.http.HttpServer 4
```

## 테스트

브라우저 여러 탭:

```text
http://localhost:8080/
```

curl 여러 개:

```bash
for i in {1..20}; do
  curl http://localhost:8080/ &
done
```

확인할 로그:

```text
[boss] accepted connection conn-1
[boss] assigned conn-1 -> worker-1
[worker-1] registered conn-1
[worker-1] request GET / on conn-1
[worker-1] response complete conn-1
[boss] assigned conn-2 -> worker-2
[worker-2] registered conn-2
```

같은 keep-alive connection의 다음 요청은 같은 worker에서 처리됩니다.
