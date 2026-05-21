# HTTP Request Observation Server

브라우저와 curl이 보내는 실제 HTTP 요청 문자열을 관찰하기 위한 NIO 서버입니다.

이 문서가 설명하는 파일:

```text
src/main/java/httpserver/nio/http/HttpRequestObservationServer.java
```

이번 단계의 목적은 HTTP 요청을 완전히 파싱하는 것이 아닙니다. 요청을 raw text로 출력하고, Request Line과 Header 구조를 눈으로 확인하는 것이 목적입니다.

아직 구현하지 않는 것:

- HTTP Request Parser 클래스
- Router
- GET/POST 분기
- Keep-Alive
- Static File Server

## 구현 범위

- 기존 NIO HTTP 서버의 Selector/Event Loop 구조 유지
- `ServerSocketChannel`, `SocketChannel`, `Selector` 사용
- 클라이언트 요청을 `ByteBuffer`로 읽기
- 읽은 데이터를 UTF-8 문자열로 변환
- HTTP 요청 전체 raw text 출력
- Request Line 별도 출력
- Request Line을 `method`, `path`, `version`으로 나누어 출력
- Header를 줄 단위로 출력
- Header 개수 출력
- `\r\n\r\n` 기준으로 Header 종료 감지
- 응답은 기존처럼 `Hello NIO` 반환

## 실행 방법

IntelliJ에서 실행:

1. `HttpRequestObservationServer.java` 파일을 엽니다.
2. `public static void main(String[] args)` 왼쪽 실행 버튼을 누릅니다.
3. `Run 'HttpRequestObservationServer.main()'`을 선택합니다.

Gradle로 실행:

```bash
gradle run
```

또는 명시적으로:

```bash
gradle runHttpRequestObservationServer
```

JDK로 직접 실행:

```bash
javac -d build/classes/java/main src/main/java/httpserver/nio/http/HttpRequestObservationServer.java
java -cp build/classes/java/main httpserver.nio.http.HttpRequestObservationServer
```

## 테스트 방법

브라우저:

```text
http://localhost:8080
```

브라우저는 대략 다음과 같은 요청을 보냅니다.

```http
GET / HTTP/1.1
Host: localhost:8080
User-Agent: ...
```

브라우저는 `Accept`, `Accept-Language`, `Accept-Encoding`, `Cookie`, `sec-ch-ua` 같은 많은 Header를 보낼 수 있습니다. 또한 `/favicon.ico`를 추가로 요청할 수 있습니다.

curl:

```bash
curl localhost:8080
```

curl은 대략 다음과 같은 요청을 보냅니다.

```http
GET / HTTP/1.1
Host: localhost:8080
User-Agent: curl/...
Accept: */*
```

nc:

```bash
nc localhost 8080
```

아래 요청을 직접 입력한 뒤 빈 줄까지 입력합니다.

```http
GET /hello HTTP/1.1
Host: localhost

```

터미널에서 한 번에 보내려면:

```bash
printf 'GET /hello HTTP/1.1\r\nHost: localhost\r\n\r\n' | nc localhost 8080
```

## HTTP Request 구조

HTTP 요청은 크게 세 부분으로 볼 수 있습니다.

```text
Request Line
Headers
Empty Line
```

예시:

```http
GET /hello HTTP/1.1
Host: localhost
User-Agent: curl/...

```

## Request Line 구조

Request Line은 요청의 첫 줄입니다.

```text
GET /hello HTTP/1.1
```

세 부분으로 나눌 수 있습니다.

- Method: `GET`
- Path: `/hello`
- Version: `HTTP/1.1`

이번 서버는 이 세 값을 콘솔에 따로 출력합니다.

## Header란 무엇인가?

Header는 요청에 대한 부가 정보입니다.

예를 들어 `Host`, `User-Agent`, `Accept` 같은 값들이 Header입니다.

```http
Host: localhost:8080
User-Agent: curl/8.7.1
Accept: */*
```

이번 서버는 Header를 Map에 저장하지 않고 줄 단위로 출력만 합니다.

## Host Header가 왜 필요한가?

HTTP/1.1에서는 `Host` Header가 중요합니다.

같은 IP와 포트에 여러 도메인이 연결될 수 있기 때문에, 클라이언트가 어떤 host로 요청했는지 서버에게 알려줘야 합니다.

로컬 테스트에서는 보통 다음처럼 보입니다.

```http
Host: localhost:8080
```

## User-Agent 의미

`User-Agent`는 요청을 보낸 클라이언트 프로그램 정보를 담습니다.

브라우저는 Chrome, Safari 같은 긴 문자열을 보내고, curl은 `curl/8.7.1`처럼 비교적 짧은 값을 보냅니다.

이 차이를 보면 브라우저 요청과 curl 요청을 구분할 수 있습니다.

## 왜 브라우저가 여러 Header를 보내는가?

브라우저는 단순히 문서만 요청하지 않습니다.

브라우저는 서버에게 다음과 같은 정보를 함께 알려줍니다.

- 어떤 응답 형식을 선호하는지
- 어떤 언어를 선호하는지
- 압축을 받을 수 있는지
- 어떤 브라우저와 운영체제인지
- 기존 쿠키가 있는지
- 보안/탐색 맥락이 무엇인지

그래서 curl보다 브라우저 요청 Header가 훨씬 많습니다.

## 왜 \r\n 이 중요한가?

HTTP/1.x는 줄 구분에 CRLF, 즉 `\r\n`을 사용합니다.

요청의 각 줄은 다음처럼 끝납니다.

```text
GET / HTTP/1.1\r\n
Host: localhost:8080\r\n
```

## 왜 \r\n\r\n 이 Header 종료인가?

Header는 여러 줄로 이어지고, 빈 줄이 나오면 Header가 끝났다는 뜻입니다.

빈 줄은 CRLF가 한 번 더 나온 형태입니다.

```text
Header: value\r\n
\r\n
```

그래서 바이트 흐름에서는 `\r\n\r\n`을 만나면 "여기까지가 Header"라고 판단할 수 있습니다.

## HTTP는 텍스트 프로토콜이다

HTTP/1.x 요청은 사람이 읽을 수 있는 텍스트 형식입니다.

브라우저가 서버에 마법 같은 객체를 보내는 것이 아니라, 결국 이런 문자열을 TCP 연결 위로 보냅니다.

```http
GET / HTTP/1.1
Host: localhost:8080
User-Agent: ...

```

## 왜 nc가 학습에 좋은가?

`nc`를 사용하면 브라우저 없이 직접 HTTP 요청 문자열을 보낼 수 있습니다.

이렇게 해보면 HTTP의 본질이 더 잘 보입니다.

```bash
printf 'GET /hello HTTP/1.1\r\nHost: localhost\r\n\r\n' | nc localhost 8080
```

즉, 브라우저가 보내는 요청도 결국 정해진 형식의 단순 문자열이라는 것을 확인할 수 있습니다.
