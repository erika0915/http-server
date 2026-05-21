# HTTP/1.1 Protocol Handling

이 문서는 Request Line/Host 검증, `Content-Length`, `Transfer-Encoding: chunked`, `HEAD` method 처리를 하나로 묶어 정리합니다.

## 목표

HTTP 요청을 단순 문자열로 보는 단계를 넘어서, HTTP/1.1 요청이 지켜야 하는 기본 규칙을 서버 코드에 반영합니다.

## 구현 내용

### Request Line 검증

Request Line은 HTTP 요청의 첫 줄입니다.

```text
GET /hello HTTP/1.1
```

세 부분으로 나뉩니다.

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

잘못된 요청은 `400 Bad Request`로 응답합니다.

### Host Header 검증

HTTP/1.1 요청에는 `Host` header가 필요합니다.

```text
GET / HTTP/1.1
Host: localhost:8080
```

`Host`가 없거나 값이 비어 있으면 `400 Bad Request`로 응답합니다.

### Content-Length Body

요청에 `Content-Length`가 있으면 header만 읽고 바로 처리하면 안 됩니다.

```text
POST /hello HTTP/1.1
Host: localhost:8080
Content-Length: 11

hello world
```

서버는 body byte가 `Content-Length`만큼 도착할 때까지 기다린 뒤 `HttpRequest.body`에 저장합니다.

현재 Router는 POST application logic을 처리하지 않으므로 응답은 `405 Method Not Allowed`일 수 있습니다.

중요한 점은 서버가 body를 기다린 뒤 요청을 파싱한다는 것입니다.

### Transfer-Encoding: chunked

`Transfer-Encoding: chunked`는 body 전체 길이를 미리 알 수 없을 때 body를 chunk 단위로 보내는 방식입니다.

예시:

```text
5
hello
6
 world
0
```

마지막 `0` chunk가 도착하면 body가 끝난 것으로 판단합니다.

파싱 결과:

```text
hello world
```

이번 구현은 학습용 첫 버전입니다.

아직 다음 기능은 구현하지 않습니다.

```text
chunk extension
trailer field
chunked response
gzip
HTTP/2
```

### HEAD Method

`HEAD`는 `GET`과 거의 같지만 response body를 전송하지 않습니다.

```text
GET  /index.html -> header + body
HEAD /index.html -> header only
```

중요한 점:

```text
Content-Length는 원래 GET body 길이를 유지한다.
실제로 전송되는 body는 없다.
```

## 코드 흐름

```text
SocketChannel read
-> Connection readBuffer 누적
-> header 종료 지점 감지
-> Content-Length 또는 chunked 여부 확인
-> body가 필요하면 body 완료까지 대기
-> HttpRequestParser.parse(rawRequest)
-> Request Line 검증
-> Header 파싱
-> HTTP/1.1이면 Host 검증
-> body 문자열 저장
-> Router.handle(request)
-> HEAD이면 response.withoutBody()
```

## 테스트 방법

정상 요청:

```bash
curl -v http://localhost:8080/
```

잘못된 Request Line:

```text
HELLO THIS IS NOT HTTP

```

Host가 없는 HTTP/1.1 요청:

```text
GET / HTTP/1.1

```

Content-Length 요청:

```bash
curl -v -X POST http://localhost:8080/hello -H "Content-Length: 11" -d "hello world"
```

chunked 요청:

```bash
nc localhost 8080
```

```text
POST /hello HTTP/1.1
Host: localhost:8080
Transfer-Encoding: chunked

5
hello
6
 world
0

```

HEAD 요청:

```bash
curl -I http://localhost:8080/
```

## 완료 기준

```text
정상 GET 요청은 기존처럼 처리된다.
잘못된 Request Line은 400 Bad Request를 반환한다.
HTTP/1.1 요청에 Host header가 없거나 비어 있으면 400 Bad Request를 반환한다.
Content-Length가 있으면 body byte가 모두 도착할 때까지 기다린다.
Transfer-Encoding: chunked 요청은 0 chunk가 도착할 때까지 기다린다.
chunk data를 조립해 HttpRequest.body에 저장한다.
HEAD 요청은 GET과 같은 status/header를 반환하되 body를 전송하지 않는다.
```
