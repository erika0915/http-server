# HTTP/1.1 Request Validation

Step 14는 HTTP 요청의 기본 형식을 검증하는 단계입니다.

이 단계에서는 Request Line과 HTTP/1.1 필수 header인 `Host`를 함께 검증합니다.

## 목표

잘못된 HTTP 요청이 들어왔을 때 서버가 정상 요청처럼 처리하지 않고 `400 Bad Request`로 응답하도록 만듭니다.

검증 대상:

```text
Request Line
Host Header
```

## 구현 내용

`HttpRequestParser`에 HTTP 요청 검증을 추가했습니다.

Request Line 예시:

```text
GET /hello HTTP/1.1
```

위 한 줄은 다음 세 부분으로 구성됩니다.

```text
method  = GET
path    = /hello
version = HTTP/1.1
```

검증 항목:

```text
method  -> 대문자 알파벳 token
path    -> / 로 시작하는 경로
version -> HTTP/1.0 또는 HTTP/1.1
```

HTTP/1.1 요청에서는 `Host` header도 검증합니다.

```text
HTTP/1.1 요청이면 Host header가 필요하다.
Host header 값은 비어 있으면 안 된다.
HTTP/1.0 요청에는 Host 필수 검증을 적용하지 않는다.
```

## 잘못된 요청 예시

Request Line이 잘못된 경우:

```text
HELLO THIS IS NOT HTTP
GET
GET hello HTTP/1.1
GET / HTTP/2.0
```

Host header가 없는 경우:

```text
GET / HTTP/1.1
```

Host header 값이 비어 있는 경우:

```text
GET / HTTP/1.1
Host:
```

이런 요청은 parser에서 `IllegalArgumentException`이 발생하고, `WorkerEventLoop`가 이를 잡아서 `400 Bad Request` 응답을 준비합니다.

## 핵심 개념

HTTP는 텍스트 프로토콜이지만 아무 문자열이나 HTTP 요청이 되는 것은 아닙니다.

서버는 최소한 다음을 확인해야 합니다.

```text
요청이 method/path/version 세 부분으로 나뉘는가?
method가 올바른 token인가?
path가 요청 경로처럼 생겼는가?
version이 서버가 이해할 수 있는 HTTP 버전인가?
HTTP/1.1 요청에 Host header가 있는가?
```

`Host` header는 클라이언트가 어떤 host에 요청을 보내는지 나타냅니다.

같은 서버 IP와 port에서 여러 도메인을 처리할 수 있기 때문에, HTTP/1.1에서는 `Host` header가 중요합니다.

이번 프로젝트에서는 아직 virtual host 라우팅까지 구현하지 않습니다. 다만 HTTP/1.1 요청에 `Host`가 필요하다는 규칙은 먼저 검증합니다.

## 코드 흐름

```text
SocketChannel read
-> Connection readBuffer 누적
-> \r\n\r\n 감지
-> HttpRequestParser.parse(rawRequest)
-> Request Line 검증
-> Header 파싱
-> HTTP/1.1이면 Host header 검증
-> 정상 요청이면 HttpRequest 생성
-> 잘못된 요청이면 IllegalArgumentException
-> WorkerEventLoop에서 400 Bad Request 응답
```

## 실행 방법

서버를 실행합니다.

```bash
java -cp build/classes/java/main httpserver.nio.http.HttpServer
```

## 테스트 방법

정상 요청:

```bash
curl -v http://localhost:8080/
```

Request Line이 잘못된 요청:

```bash
nc localhost 8080
```

입력:

```text
HELLO THIS IS NOT HTTP

```

예상 결과:

```text
HTTP/1.1 400 Bad Request
```

`Host`가 없는 HTTP/1.1 요청:

```bash
nc localhost 8080
```

입력:

```text
GET / HTTP/1.1

```

예상 결과:

```text
HTTP/1.1 400 Bad Request
```

HTTP/1.0 요청은 이번 Host 검증 대상이 아닙니다.

```text
GET / HTTP/1.0

```

## 완료 기준

```text
정상 GET 요청은 기존처럼 처리된다.
잘못된 Request Line은 400 Bad Request를 반환한다.
HTTP/1.1 요청에 Host header가 없으면 400 Bad Request를 반환한다.
HTTP/1.1 요청에 Host header 값이 비어 있으면 400 Bad Request를 반환한다.
Content-Length body 처리는 아직 하지 않는다.
Transfer-Encoding chunked 처리는 아직 하지 않는다.
```
