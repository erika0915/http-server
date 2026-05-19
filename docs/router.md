# Router and HttpResponse

HTTP 요청을 `HttpRequest` 객체로 파싱한 뒤, URL path에 따라 다른 `HttpResponse`를 반환하는 단계입니다.

관련 파일:

```text
src/main/java/dev/httpserver/nio/http/HttpServer.java
src/main/java/dev/httpserver/nio/http/request/HttpRequest.java
src/main/java/dev/httpserver/nio/http/request/HttpRequestParser.java
src/main/java/dev/httpserver/nio/http/response/HttpResponse.java
src/main/java/dev/httpserver/nio/http/router/Router.java
```

아직 구현하지 않는 것:

- Static File Server
- Keep-Alive
- POST Body 처리
- 멀티스레드 구조

## 요청 흐름

```text
SocketChannel
-> ByteBuffer
-> raw HTTP request string
-> HttpRequestParser
-> HttpRequest
-> Router
-> HttpResponse
-> HTTP response bytes
-> SocketChannel
```

## 지원 경로

```text
GET /        -> 200 OK, text/plain, Hello NIO HTTP Server
GET /hello   -> 200 OK, text/plain, Hello Router
GET /users   -> 200 OK, application/json, [{"id":1,"name":"soohee"},{"id":2,"name":"nio"}]
GET /unknown -> 404 Not Found
POST /hello  -> 405 Method Not Allowed
```

## 실행 방법

IntelliJ에서 실행:

1. `HttpServer.java` 파일을 엽니다.
2. `public static void main(String[] args)` 왼쪽 실행 버튼을 누릅니다.
3. `Run 'HttpServer.main()'`을 선택합니다.

Gradle:

```bash
gradle run
```

또는:

```bash
gradle runHttpServer
```

## 테스트 방법

브라우저:

```text
http://localhost:8080/
http://localhost:8080/hello
http://localhost:8080/users
http://localhost:8080/unknown
```

curl:

```bash
curl -v http://localhost:8080/
curl -v http://localhost:8080/hello
curl -v http://localhost:8080/users
curl -v http://localhost:8080/unknown
curl -v -X POST http://localhost:8080/hello
```

nc:

```bash
printf 'GET /hello HTTP/1.1\r\nHost: localhost:8080\r\n\r\n' | nc localhost 8080
```

## Router가 왜 필요한가?

Parser는 요청을 객체로 바꾸는 역할만 합니다.

Router는 그 요청을 보고 어떤 응답을 만들지 결정합니다.

예를 들어 `GET /hello`가 들어오면 `/hello` handler를 선택하고, `GET /users`가 들어오면 `/users` handler를 선택합니다.

## DispatcherServlet과의 관계

Spring MVC의 `DispatcherServlet`은 요청을 받아 적절한 controller/handler로 보내는 중앙 진입점입니다.

이번 `Router`는 그 아이디어를 아주 작게 만든 것입니다. 요청 path를 보고 직접 `HttpResponse`를 선택합니다.

## Status Code

`200 OK`는 요청을 정상 처리했다는 뜻입니다.

`404 Not Found`는 요청한 path에 해당하는 route가 없다는 뜻입니다.

`405 Method Not Allowed`는 path 이전에, 현재 서버가 해당 HTTP method를 지원하지 않는다는 뜻입니다.

## Content-Type

`Content-Type`은 응답 body가 어떤 형식인지 알려줍니다.

일반 텍스트는 `text/plain`, JSON은 `application/json`을 사용합니다.

브라우저와 클라이언트는 이 값을 보고 body를 어떻게 해석할지 결정할 수 있습니다.

## Content-Length

`Content-Length`는 body의 바이트 길이입니다.

문자열 길이가 아니라 UTF-8 바이트 길이로 계산해야 합니다. 한글처럼 한 문자가 여러 바이트인 경우가 있기 때문입니다.

## Connection: close

아직 Keep-Alive를 구현하지 않았기 때문에 모든 응답은 `Connection: close`를 사용합니다.

서버는 응답을 보낸 뒤 channel을 닫습니다.
