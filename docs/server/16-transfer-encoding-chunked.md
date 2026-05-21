# Transfer-Encoding Chunked

Step 16은 `Transfer-Encoding: chunked` 요청 body를 처리하는 단계입니다.

## 목표

`Content-Length`는 body 전체 길이를 미리 알려주는 방식입니다.

반면 `Transfer-Encoding: chunked`는 body를 여러 chunk로 나누어 보내고, 마지막에 `0` chunk로 종료를 알립니다.

이번 단계에서는 가장 단순한 chunked body를 읽고 일반 body 문자열로 조립합니다.

## 구현 내용

`Connection`이 `Transfer-Encoding: chunked` header를 감지하면 마지막 chunk가 도착할 때까지 기다립니다.

마지막 chunk:

```text
0\r\n\r\n
```

`HttpRequestParser`는 chunked body를 다음 방식으로 해석합니다.

```text
chunk size line
chunk data
chunk size line
chunk data
0
```

예시:

```text
5
hello
6
 world
0
```

파싱 결과:

```text
hello world
```

## 핵심 개념

chunked body는 body 길이를 미리 알 수 없을 때 사용합니다.

서버는 처음부터 전체 body 크기를 알 수 없기 때문에, chunk size를 하나씩 읽어가며 body를 조립해야 합니다.

이번 구현은 학습용 첫 버전입니다.

아직 다음 기능은 구현하지 않습니다.

```text
chunk extension
trailer field
chunked response
gzip
HTTP/2
```

## 코드 흐름

```text
SocketChannel read
-> Connection readBuffer에 누적
-> header 종료 지점 감지
-> Transfer-Encoding: chunked 확인
-> 0 chunk가 도착할 때까지 대기
-> HttpRequestParser.parse(rawRequest)
-> chunk body decode
-> HttpRequest.body 저장
```

## 실행 방법

서버를 실행합니다.

```bash
java -cp build/classes/java/main httpserver.nio.http.HttpServer
```

## 테스트 방법

`nc`로 chunked 요청을 보냅니다.

```bash
nc localhost 8080
```

입력:

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

현재 Router는 아직 POST를 처리하지 않으므로 응답은 `405 Method Not Allowed`가 정상입니다.

중요한 점은 서버가 `0` chunk가 올 때까지 기다린 뒤 요청을 파싱한다는 것입니다.

## 완료 기준

```text
Transfer-Encoding: chunked 요청을 감지한다.
0 chunk가 도착하기 전에는 요청을 처리하지 않는다.
chunk data를 조립해 HttpRequest.body에 저장한다.
chunk extension과 trailer field는 아직 처리하지 않는다.
chunked response는 아직 구현하지 않는다.
```
