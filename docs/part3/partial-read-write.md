# Partial Read / Partial Write

Step 10은 NIO 서버가 `read()` 한 번, `write()` 한 번에 의존하지 않도록 바꾸는 단계입니다.

관련 파일:

```text
src/main/java/httpserver/nio/http/HttpServer.java
src/main/java/httpserver/nio/http/Connection.java
src/main/java/httpserver/nio/http/ConnectionState.java
```

## 왜 필요한가?

TCP는 메시지 단위가 아니라 byte stream입니다.

즉 클라이언트가 HTTP 요청 하나를 보냈더라도 서버 입장에서는 이렇게 나뉘어 도착할 수 있습니다.

```text
read #1: GET / HTTP/1.1\r\n
read #2: Host: localhost:8080\r\n
read #3: \r\n
```

그래서 `read()` 한 번이 HTTP 요청 하나를 의미하지 않습니다.

반대로 응답도 마찬가지입니다. 서버가 큰 HTML, CSS, JS, 이미지 파일을 쓰려고 해도 `write()` 한 번에 전부 전송되지 않을 수 있습니다.

## Partial Read

`Connection`은 `readBuffer`를 소유합니다.

요청이 들어오면 `readBuffer`에 데이터를 누적합니다.

```text
\r\n\r\n
```

이 표시가 보이면 HTTP Header가 끝났다고 판단합니다.

그 전까지는 Router를 호출하지 않고 계속 `OP_READ` 상태로 기다립니다.

## Partial Write

`HttpResponse`가 만들어지면 응답 byte 배열을 `writeBuffer`에 담습니다.

그 다음 `SelectionKey`의 관심 이벤트를 `OP_WRITE`로 바꿉니다.

`SocketChannel.write(writeBuffer)` 호출 후 아직 남은 바이트가 있으면 `OP_WRITE`를 유지합니다.

전송이 끝나면:

```text
keep-alive true  -> READING 상태로 돌아감
keep-alive false -> 연결 종료
```

## compact()와 clear()

`clear()`는 데이터를 실제로 지우는 것이 아니라 `position=0`, `limit=capacity`로 되돌립니다.

이번 단계에서는 HTTP pipelining을 완성하지 않으므로 요청 하나 처리가 끝난 뒤 `clear()`로 다음 요청을 받을 준비를 합니다.

`compact()`는 아직 읽지 않은 데이터를 buffer 앞쪽으로 당기는 메서드입니다.

나중에 한 번의 readBuffer 안에 다음 요청 일부까지 들어오는 HTTP pipelining을 제대로 처리하려면 `compact()`가 중요해집니다.

## OP_WRITE를 항상 등록하면 안 되는 이유

대부분의 소켓은 쓰기 가능한 상태인 경우가 많습니다.

그래서 `OP_WRITE`를 항상 등록하면 Selector가 계속 깨어나서 CPU를 낭비할 수 있습니다.

이번 서버는 응답할 데이터가 생겼을 때만 `OP_WRITE`를 등록하고, 응답이 끝나면 다시 `OP_READ`로 바꿉니다.

## 로그 예시

```text
[conn-1] read 16 bytes
[conn-1] request incomplete, waiting for more data
[conn-1] read 23 bytes
[conn-1] request complete
[conn-1] state READING -> WRITING
[conn-1] wrote 1024 bytes
[conn-1] write incomplete, remaining=512
[conn-1] wrote 512 bytes
[conn-1] response complete
[conn-1] keep-alive true
[conn-1] state WRITING -> READING
```

## 테스트

브라우저:

```text
http://localhost:8080/
```

curl:

```bash
curl -v http://localhost:8080/
curl -v http://localhost:8080/style.css
```

nc로 partial read 테스트:

```bash
nc localhost 8080
```

먼저 일부만 입력합니다.

```text
GET / HTTP/1.1
```

잠시 후 다음 줄을 입력합니다.

```text
Host: localhost:8080
```

마지막으로 빈 줄을 한 번 더 입력하면 요청이 완성됩니다.

서버 로그에서 `request incomplete` 이후 `request complete`가 출력되면 partial read 흐름이 동작한 것입니다.
