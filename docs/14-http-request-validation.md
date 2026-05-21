# HTTP/1.1 Request Validation

Step 14는 HTTP 요청의 첫 줄인 Request Line을 검증하는 단계입니다.

## 목표

잘못된 HTTP 요청이 들어왔을 때 서버가 정상 요청처럼 처리하지 않고 `400 Bad Request`로 응답하도록 만듭니다.

이번 단계에서는 Request Line만 검증합니다.

```text
GET /hello HTTP/1.1
```

위 한 줄은 다음 세 부분으로 구성됩니다.

```text
method  = GET
path    = /hello
version = HTTP/1.1
```

## 구현 내용

`HttpRequestParser`에 Request Line 검증을 추가했습니다.

검증 항목:

```text
method  -> 대문자 알파벳 token
path    -> / 로 시작하는 경로
version -> HTTP/1.0 또는 HTTP/1.1
```

잘못된 요청 예시:

```text
HELLO THIS IS NOT HTTP
GET
GET hello HTTP/1.1
GET / HTTP/2.0
```

이런 요청은 parser에서 `IllegalArgumentException`이 발생하고, `WorkerEventLoop`가 이를 잡아서 `400 Bad Request` 응답을 준비합니다.

## 핵심 개념

HTTP는 텍스트 프로토콜이지만 아무 문자열이나 HTTP 요청이 되는 것은 아닙니다.

서버는 최소한 다음을 확인해야 합니다.

```text
요청이 세 부분으로 나뉘는가?
method가 올바른 token인가?
path가 요청 경로처럼 생겼는가?
version이 서버가 이해할 수 있는 HTTP 버전인가?
```

이 검증이 없으면 잘못된 요청이 Router나 StaticFileHandler까지 흘러갈 수 있습니다.

## 코드 흐름

```text
SocketChannel read
-> Connection readBuffer 누적
-> \r\n\r\n 감지
-> HttpRequestParser.parse(rawRequest)
-> Request Line 검증
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

잘못된 요청:

```bash
nc localhost 8080
```

그리고 다음 문자열을 입력합니다.

```text
HELLO THIS IS NOT HTTP

```

예상 결과:

```text
HTTP/1.1 400 Bad Request
```

## 완료 기준

```text
정상 GET 요청은 기존처럼 처리된다.
잘못된 Request Line은 400 Bad Request를 반환한다.
Host Header 검증은 아직 하지 않는다.
Content-Length body 처리는 아직 하지 않는다.
Transfer-Encoding chunked 처리는 아직 하지 않는다.
```
