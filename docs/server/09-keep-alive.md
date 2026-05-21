# HTTP Keep-Alive

Keep-Alive는 하나의 TCP 연결에서 HTTP 요청과 응답을 여러 번 주고받는 방식입니다.

이전 단계까지는 다음 흐름이었습니다.

```text
요청 1개 -> 응답 1개 -> 연결 종료
```

이번 단계부터는 HTTP/1.1 요청에서 `Connection: close`가 없으면 연결을 유지합니다.

```text
요청 1개 -> 응답 1개 -> 연결 유지 -> 다음 요청 처리
```

## 구현 범위

- 기존 NIO Event Loop 유지
- 기존 Router / Static File Server 유지
- `SelectionKey.attach()`로 연결 상태 저장
- `ConnectionContext`로 connection id 출력
- HTTP/1.1 기본 keep-alive 적용
- `Connection: close` 요청이면 응답 후 연결 종료
- 응답 Header에 `Connection: keep-alive` 또는 `Connection: close` 설정

아직 구현하지 않는 것:

- partial read/write 완전 처리
- `ByteBuffer.compact()` 기반 요청 누적
- timeout
- 멀티 EventLoop
- Chunked Transfer Encoding

## 로그 예시

```text
[conn-1] connected
[conn-1] request #1 GET /
[conn-1] keep-alive enabled
[conn-1] request #2 GET /style.css
[conn-1] keep-alive enabled
[conn-1] request #3 GET /app.js
[conn-1] keep-alive enabled
[conn-1] closed
```

브라우저가 같은 연결을 재사용하면 같은 `conn-n`으로 여러 요청이 찍힙니다.

## 왜 Keep-Alive가 중요한가?

TCP 연결을 새로 만들려면 handshake 비용이 듭니다.

HTML을 받은 뒤 CSS, JavaScript, 이미지까지 요청할 때 매번 새 TCP 연결을 만들면 느립니다.

Keep-Alive를 사용하면 이미 만든 연결을 재사용해서 추가 요청을 보낼 수 있습니다.

## HTTP/1.1 기본이 keep-alive인 이유

HTTP/1.1에서는 연결 재사용이 기본입니다.

클라이언트가 명시적으로 다음 Header를 보내면 서버는 응답 후 닫습니다.

```http
Connection: close
```

그렇지 않으면 서버는 응답에 다음 Header를 넣고 연결을 유지할 수 있습니다.

```http
Connection: keep-alive
```

## 브라우저의 동작

브라우저가 `/`를 요청해서 `index.html`을 받으면, HTML 안의 링크를 보고 추가 요청을 보냅니다.

```text
GET /
GET /style.css
GET /app.js
```

Keep-Alive가 가능하면 브라우저는 같은 TCP 연결을 재사용할 수 있습니다.

## 실제 서버

NGINX, Tomcat, Netty 같은 실제 서버들도 keep-alive를 사용합니다.

연결 재사용은 웹 서버 성능에서 매우 중요한 기본 기능입니다.

## 테스트 방법

브라우저:

```text
http://localhost:8080/
```

Chrome DevTools -> Network 탭에서 요청들을 확인합니다.

curl:

```bash
curl -v --http1.1 http://localhost:8080/
```

`Connection: keep-alive` 응답 Header를 확인합니다.

`Connection: close` 테스트:

```bash
curl -v --http1.1 -H 'Connection: close' http://localhost:8080/
```

nc로 같은 연결에서 여러 요청 보내기:

```bash
nc localhost 8080
```

아래 내용을 붙여 넣습니다.

```http
GET / HTTP/1.1
Host: localhost:8080
Connection: keep-alive

GET /style.css HTTP/1.1
Host: localhost:8080
Connection: close

```

현재 단계는 partial read/write를 완성하지 않았기 때문에, nc에서 두 요청을 너무 빠르게 한 번에 보내는 경우 하나의 read에 섞일 수 있습니다. 학습용으로는 첫 요청 응답을 확인한 뒤 다음 요청을 입력하는 방식이 가장 잘 보입니다.
