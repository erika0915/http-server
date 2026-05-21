# Content-Length Body Handling

Step 15는 `Content-Length` 기반 요청 body를 처리하는 단계입니다.

## 목표

HTTP 요청 header에 `Content-Length`가 있으면, 서버는 header만 읽고 바로 처리하면 안 됩니다.

`Content-Length`에 적힌 byte 수만큼 body가 모두 도착할 때까지 기다린 뒤 요청을 파싱해야 합니다.

## 구현 내용

`Connection`이 요청 완료 여부를 판단할 때 `Content-Length`를 확인하도록 변경했습니다.

이전:

```text
\r\n\r\n 발견 -> 요청 완료
```

변경 후:

```text
\r\n\r\n 발견
-> Content-Length header 확인
-> body byte가 Content-Length만큼 도착했는지 확인
-> 충분하면 요청 완료
-> 부족하면 계속 OP_READ 대기
```

`HttpRequestParser`는 `Content-Length` 값을 검증하고 body 문자열을 `HttpRequest.body`에 저장합니다.

## 핵심 개념

TCP는 byte stream입니다.

아래 요청이 한 번에 도착한다는 보장은 없습니다.

```text
POST /hello HTTP/1.1
Host: localhost:8080
Content-Length: 11

hello world
```

header만 먼저 도착하고 body는 나중에 도착할 수 있습니다.

그래서 서버는 `Content-Length`를 보고 body가 모두 도착했는지 판단해야 합니다.

## 코드 흐름

```text
SocketChannel read
-> Connection readBuffer에 누적
-> header 종료 지점 감지
-> Content-Length 확인
-> body byte 수 확인
-> 부족하면 request incomplete
-> 충분하면 HttpRequestParser.parse(rawRequest)
-> HttpRequest.body 저장
```

## 실행 방법

서버를 실행합니다.

```bash
java -cp build/classes/java/main httpserver.nio.http.HttpServer
```

## 테스트 방법

curl로 body가 있는 요청을 보냅니다.

```bash
curl -v -X POST http://localhost:8080/hello -H "Content-Length: 11" -d "hello world"
```

현재 Router는 아직 POST를 처리하지 않으므로 응답은 `405 Method Not Allowed`가 정상입니다.

중요한 점은 서버가 body를 기다린 뒤 요청을 파싱한다는 것입니다.

`nc`로 partial body를 테스트할 수도 있습니다.

```bash
nc localhost 8080
```

먼저 입력:

```text
POST /hello HTTP/1.1
Host: localhost:8080
Content-Length: 11

hello
```

이 상태에서는 body가 아직 부족합니다.

이어서 입력:

```text
 world
```

그러면 body가 완성되고 요청 처리가 진행됩니다.

## 완료 기준

```text
Content-Length가 없으면 기존처럼 header 종료만으로 요청 완료를 판단한다.
Content-Length가 있으면 body byte가 모두 도착할 때까지 기다린다.
body가 도착하면 HttpRequest.body에 저장한다.
POST routing은 아직 구현하지 않는다.
chunked body 처리는 다음 step에서 다룬다.
```
