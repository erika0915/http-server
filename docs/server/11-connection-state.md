# Connection State

Step 11은 `SocketChannel` 중심의 절차형 처리에서 `Connection` 객체 중심의 상태 관리 구조로 바꾸는 단계입니다.

관련 파일:

```text
src/main/java/httpserver/nio/http/HttpServer.java
src/main/java/httpserver/nio/http/Connection.java
src/main/java/httpserver/nio/http/ConnectionState.java
```

기존 클래스는 그대로 사용합니다.

```text
HttpRequest
HttpRequestParser
HttpResponse
Router
StaticFileHandler
```

## 왜 Connection 객체가 필요한가?

Keep-Alive 이전에는 요청 하나를 읽고, 응답 하나를 쓰고, 바로 닫으면 됐습니다.

Keep-Alive 이후에는 하나의 `SocketChannel`에서 여러 요청이 순서대로 들어올 수 있습니다.

그래서 연결마다 다음 상태를 기억해야 합니다.

- connection id
- 현재 읽는 중인지 쓰는 중인지
- read buffer
- write buffer
- keep-alive 여부
- 연결 생성 시각
- 마지막 활동 시각
- 요청 개수

이 정보가 `HttpServer` 여기저기에 흩어지면 관리가 어려워집니다. 그래서 `Connection` 객체로 묶었습니다.

## 상태 머신

상태 머신은 객체가 가질 수 있는 상태와 그 상태 전환을 명확히 표현하는 방식입니다.

이번 서버의 상태:

```text
READING
WRITING
CLOSED
```

흐름:

```text
connected
-> READING
-> request parsed
-> WRITING
-> response sent
-> keep-alive면 READING
-> close면 CLOSED
```

## Event Loop + Connection

Selector Event Loop는 여전히 그대로입니다.

```text
Selector
-> SelectionKey
-> key.attachment()
-> Connection
```

`SelectionKey.attach(connection)`을 사용하면 이벤트가 발생한 channel의 상태 객체를 바로 꺼낼 수 있습니다.

그래서 Event Loop는 `SocketChannel`을 직접 관리하기보다 `Connection`에게 일을 맡깁니다.

## Netty/Tomcat/NGINX와의 관계

Netty에도 `Channel`이라는 연결 중심 추상화가 있습니다.

Tomcat과 NGINX도 내부적으로 연결 상태를 관리합니다.

실제 서버는 timeout, partial read/write, backpressure, keep-alive limit 같은 훨씬 많은 상태를 관리하지만, 기본 아이디어는 같습니다.

```text
연결마다 상태를 가진 객체가 필요하다
```

## ByteBuffer를 Connection이 소유하는 이유

`ByteBuffer`는 네트워크 연결의 현재 입출력 상태입니다.

Keep-Alive에서는 같은 연결에서 다음 요청이 이어질 수 있으므로 buffer도 연결 단위로 관리하는 편이 자연스럽습니다.

이번 단계에서는 partial read/write를 완성하지 않지만, 다음 단계로 가려면 `Connection`이 buffer를 소유하는 구조가 필요합니다.

## 로그 예시

```text
[conn-1] connected
[conn-1] state READING
[conn-1] request #1 GET /
[conn-1] state READING -> WRITING
[conn-1] response sent
[conn-1] keep-alive true
[conn-1] state WRITING -> READING
[conn-1] request #2 GET /style.css
[conn-1] state READING -> WRITING
[conn-1] response sent
[conn-1] keep-alive true
[conn-1] state WRITING -> READING
[conn-1] closed
```

## 테스트

브라우저:

```text
http://localhost:8080/
```

브라우저가 `/`, `/style.css`, `/app.js`를 요청하는지 확인합니다.

curl:

```bash
curl -v --http1.1 http://localhost:8080/ http://localhost:8080/style.css http://localhost:8080/app.js
```

서버 로그에서 같은 connection id로 여러 요청이 처리되는지 봅니다.

현재 단계에서 하지 않는 것:

- partial read/write 완전 처리
- timeout
- selector interestOps 최적화
- worker thread
- request queue
