# Minimal NIO HTTP Server

NIO Echo Server 다음 단계로, 브라우저와 curl이 실제 HTTP 서버로 인식할 수 있는 최소 HTTP 응답을 반환하는 서버입니다.

이 문서가 설명하는 파일:

```text
src/main/java/dev/httpserver/nio/http/MinimalHttpServer.java
```

아직 구현하지 않는 것:

- HTTP Request Parser
- GET/POST 분기
- Router
- Keep-Alive
- Static File Server
- HTTP 요청 body 처리

## 구현 범위

- `ServerSocketChannel` 사용
- `Selector` 사용
- `localhost:8080`에서 실행
- `configureBlocking(false)` 사용
- `OP_ACCEPT` 처리
- `OP_READ` 처리
- 클라이언트 요청 데이터는 읽어서 콘솔에 출력만 함
- 브라우저 요청이 오면 항상 같은 HTTP 응답 반환

## 반환하는 HTTP 응답

서버는 항상 다음 형태의 응답을 보냅니다.

```http
HTTP/1.1 200 OK
Content-Type: text/plain
Content-Length: 9
Connection: close

Hello NIO
```

실제 네트워크 바이트에서는 줄바꿈이 반드시 CRLF, 즉 `\r\n` 형식입니다.

```text
HTTP/1.1 200 OK\r\n
Content-Type: text/plain\r\n
Content-Length: 9\r\n
Connection: close\r\n
\r\n
Hello NIO
```

## 실행 방법

IntelliJ에서 실행:

1. `MinimalHttpServer.java` 파일을 엽니다.
2. `public static void main(String[] args)` 왼쪽 실행 버튼을 누릅니다.
3. `Run 'MinimalHttpServer.main()'`을 선택합니다.

Gradle로 실행:

```bash
gradle run
```

또는 명시적으로:

```bash
gradle runMinimalHttpServer
```

JDK로 직접 실행:

```bash
javac -d build/classes/java/main src/main/java/dev/httpserver/nio/http/MinimalHttpServer.java
java -cp build/classes/java/main dev.httpserver.nio.http.MinimalHttpServer
```

## 테스트 방법

브라우저:

```text
http://localhost:8080
```

브라우저 화면에 다음이 보이면 성공입니다.

```text
Hello NIO
```

curl:

```bash
curl localhost:8080
```

응답:

```text
Hello NIO
```

헤더까지 확인:

```bash
curl -i localhost:8080
```

직접 HTTP 요청 보내기:

```bash
printf 'GET / HTTP/1.1\r\nHost: localhost\r\n\r\n' | nc localhost 8080
```

## 왜 브라우저는 HTTP 형식을 요구하는가?

브라우저는 TCP 바이트를 아무 문자열로 해석하지 않습니다. `http://localhost:8080`으로 접속하면 HTTP 프로토콜 규칙에 맞는 응답을 기대합니다.

응답이 단순히 `Hello NIO`만 있으면 브라우저는 상태 코드, 헤더, 본문 경계를 알 수 없습니다. 그래서 `HTTP/1.1 200 OK`, 헤더들, 빈 줄, 본문이라는 구조가 필요합니다.

## Echo Server와 HTTP Server의 차이

Echo Server는 클라이언트가 보낸 데이터를 그대로 돌려줍니다.

HTTP Server는 클라이언트 요청을 받은 뒤 HTTP 응답 형식에 맞춰 상태 줄, 헤더, 빈 줄, 본문을 보냅니다.

이번 서버는 요청을 파싱하지 않지만, 응답은 HTTP 형식으로 만듭니다.

## HTTP Response 구조

HTTP 응답은 크게 네 부분입니다.

```text
Status Line
Headers
Empty Line
Body
```

이번 서버의 응답:

```text
HTTP/1.1 200 OK
Content-Type: text/plain
Content-Length: 9
Connection: close

Hello NIO
```

## HTTP/1.1 200 OK 의미

`HTTP/1.1`은 응답이 HTTP/1.1 형식이라는 뜻입니다.

`200`은 요청이 성공했다는 상태 코드입니다.

`OK`는 사람이 읽기 쉬운 상태 설명입니다.

## Content-Length가 왜 필요한가?

`Content-Length`는 응답 본문이 몇 바이트인지 알려줍니다.

이번 body는 `Hello NIO`이고 UTF-8 기준 9바이트입니다.

브라우저와 curl은 이 값을 보고 본문을 어디까지 읽어야 하는지 판단할 수 있습니다.

## Connection: close 의미

`Connection: close`는 이 응답을 보낸 뒤 TCP 연결을 닫겠다는 뜻입니다.

Keep-Alive를 구현하지 않았기 때문에, 이 서버는 응답을 한 번 보내고 바로 channel을 닫습니다.

## 왜 \r\n\r\n 이 중요한가?

HTTP에서는 헤더 줄 끝에 `\r\n`을 사용합니다.

그리고 헤더와 body 사이에는 빈 줄이 필요합니다. 그 빈 줄이 바로 `\r\n\r\n`입니다.

브라우저와 curl은 `\r\n\r\n`을 보고 "여기까지가 헤더이고, 이제부터가 body"라고 판단합니다.

## 브라우저와 curl은 어떻게 동작하는가?

브라우저나 curl은 서버에 접속하면 먼저 HTTP 요청을 보냅니다.

예를 들어 curl은 대략 이런 요청을 보냅니다.

```http
GET / HTTP/1.1
Host: localhost:8080
User-Agent: curl/...
Accept: */*
```

서버는 이 요청을 콘솔에 출력합니다. 하지만 아직 파싱하지는 않습니다. 어떤 path로 요청하든 항상 같은 응답을 반환합니다.
