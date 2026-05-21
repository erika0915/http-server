# Host Header Validation

Step 15는 HTTP/1.1 요청에서 `Host` header를 검증하는 단계입니다.

## 목표

HTTP/1.1 요청에는 `Host` header가 필요합니다.

이번 단계에서는 `HTTP/1.1` 요청에 `Host` header가 없거나 값이 비어 있으면 `400 Bad Request`로 응답하도록 구현합니다.

## 구현 내용

`HttpRequestParser`에 필수 header 검증을 추가했습니다.

검증 규칙:

```text
HTTP/1.1 요청이면 Host header가 필요하다.
Host header 값은 비어 있으면 안 된다.
HTTP/1.0 요청에는 이 검증을 적용하지 않는다.
```

정상 요청:

```text
GET / HTTP/1.1
Host: localhost:8080
```

잘못된 요청:

```text
GET / HTTP/1.1
```

또는:

```text
GET / HTTP/1.1
Host:
```

## 핵심 개념

`Host` header는 클라이언트가 어떤 host에 요청을 보내는지 나타냅니다.

예를 들어 같은 서버 IP와 port에서 여러 도메인을 처리할 수 있습니다.

```text
example.com
api.example.com
static.example.com
```

이때 서버는 `Host` header를 보고 어떤 host로 온 요청인지 구분할 수 있습니다.

이번 프로젝트에서는 아직 virtual host 라우팅까지 구현하지 않습니다. 하지만 HTTP/1.1 요청에서 `Host`가 필수라는 규칙은 먼저 검증합니다.

## 코드 흐름

```text
SocketChannel read
-> Connection readBuffer 누적
-> HttpRequestParser.parse(rawRequest)
-> Request Line 검증
-> Header 파싱
-> HTTP/1.1이면 Host header 검증
-> Host가 없거나 비어 있으면 IllegalArgumentException
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

`nc`로 `Host`가 없는 요청을 보냅니다.

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

HTTP/1.0 요청은 이번 검증 대상이 아닙니다.

```text
GET / HTTP/1.0

```

## 완료 기준

```text
HTTP/1.1 요청에 Host header가 있으면 정상 처리된다.
HTTP/1.1 요청에 Host header가 없으면 400 Bad Request를 반환한다.
HTTP/1.1 요청에 Host header 값이 비어 있으면 400 Bad Request를 반환한다.
Content-Length body 처리는 아직 하지 않는다.
Transfer-Encoding chunked 처리는 아직 하지 않는다.
```
