# Connection Lifecycle

이 문서는 Keep-Alive, Partial Read/Write, Connection 상태 관리, Timeout/Cleanup을 하나의 연결 lifecycle로 묶어 정리합니다.

HTTP 서버는 요청 문자열만 처리하는 프로그램이 아니라 TCP 연결을 계속 관리하는 프로그램입니다.

## 목표

하나의 `SocketChannel`이 생성된 뒤 닫히기까지 서버가 어떤 상태를 관리해야 하는지 이해합니다.

```text
connected
-> reading request
-> writing response
-> keep-alive wait
-> next request
-> timeout or close
```

## 구현 내용

### Keep-Alive

초기 서버는 요청 하나를 처리하면 바로 연결을 닫았습니다.

```text
요청 1개 -> 응답 1개 -> 연결 종료
```

HTTP/1.1에서는 기본적으로 TCP 연결을 재사용할 수 있습니다.

```text
요청 1개 -> 응답 1개 -> 연결 유지 -> 다음 요청 처리
```

요청에 다음 header가 있으면 응답 후 연결을 닫습니다.

```text
Connection: close
```

그렇지 않은 HTTP/1.1 요청은 keep-alive로 처리합니다.

### Partial Read

TCP는 message 단위가 아니라 byte stream입니다.

클라이언트가 HTTP 요청 하나를 보냈더라도 서버 입장에서는 여러 번에 나뉘어 도착할 수 있습니다.

```text
read #1: GET / HTTP/1.1\r\n
read #2: Host: localhost:8080\r\n
read #3: \r\n
```

그래서 `read()` 한 번이 HTTP 요청 하나를 의미하지 않습니다.

`Connection`은 `readBuffer`에 데이터를 누적하고, 요청이 완성되기 전까지 Router를 호출하지 않습니다.

### Partial Write

응답도 `write()` 한 번에 전부 전송된다고 보장할 수 없습니다.

`Connection`은 응답 byte를 `writeBuffer`에 담고, `writeBuffer.hasRemaining()`이 `false`가 될 때까지 `OP_WRITE` 이벤트에서 이어서 전송합니다.

전송이 끝나면 다음 중 하나로 처리합니다.

```text
keep-alive true  -> READING 상태로 돌아감
keep-alive false -> 연결 종료
```

### Connection State

Keep-Alive 이후에는 연결마다 상태를 기억해야 합니다.

```text
connection id
read buffer
write buffer
current request
pending response
keep-alive 여부
마지막 활동 시각
요청 개수
```

그래서 `SocketChannel`만 직접 다루지 않고 `Connection` 객체를 둡니다.

상태는 다음처럼 전환됩니다.

```text
READING -> WRITING -> READING
READING -> WRITING -> CLOSED
READING -> CLOSED
```

`SelectionKey.attach(connection)`을 사용하면 Selector 이벤트가 발생했을 때 해당 연결 상태를 바로 꺼낼 수 있습니다.

### Timeout / Cleanup

Keep-Alive 연결은 계속 살아 있을 수 있습니다.

하지만 클라이언트가 연결만 열어 두고 아무 요청도 보내지 않으면 서버는 `SocketChannel`, `SelectionKey`, `ByteBuffer`, `Connection` 객체를 계속 들고 있게 됩니다.

그래서 idle timeout을 둡니다.

```text
30초 동안 read/write 활동이 없으면 연결 종료
```

닫힌 연결은 active connection 목록에서 제거합니다.

## 핵심 개념

`clear()`는 buffer의 데이터를 실제로 지우는 것이 아니라 `position=0`, `limit=capacity`로 되돌립니다.

이번 서버는 HTTP pipelining을 완성하지 않으므로 요청 하나 처리가 끝난 뒤 `clear()`로 다음 요청을 받을 준비를 합니다.

`compact()`는 아직 읽지 않은 데이터를 buffer 앞쪽으로 당기는 메서드입니다.

나중에 한 번의 read 안에 다음 요청 일부까지 들어오는 상황을 완전히 처리하려면 `compact()`가 중요해집니다.

`OP_WRITE`는 응답할 데이터가 있을 때만 등록해야 합니다.

대부분의 소켓은 쓰기 가능한 상태인 경우가 많기 때문에 `OP_WRITE`를 항상 등록하면 Selector가 계속 깨어나 CPU를 낭비할 수 있습니다.

## 코드 흐름

```text
WorkerEventLoop
-> SelectionKey에서 Connection 꺼냄
-> OP_READ이면 Connection.appendReadData()
-> 요청 미완성이면 OP_READ 유지
-> 요청 완성이면 HttpRequestParser.parse()
-> Router.handle()
-> Connection.prepareResponse()
-> OP_WRITE 등록
-> Connection.writePendingResponse()
-> 응답 완료 후 keep-alive면 OP_READ
-> close면 SelectionKey cancel + Connection close
```

## 로그 예시

```text
[conn-1] connected
[conn-1] state READING
[conn-1] read 32 bytes
[conn-1] request incomplete, waiting for more data
[conn-1] request complete
[conn-1] state READING -> WRITING
[conn-1] wrote 1024 bytes
[conn-1] response complete
[conn-1] keep-alive true
[conn-1] state WRITING -> READING
[conn-1] idle timeout after 30000ms
[conn-1] closed reason=idle-timeout
```

## 테스트 방법

Keep-Alive 확인:

```bash
curl -v --http1.1 http://localhost:8080/
curl -v --http1.1 -H 'Connection: close' http://localhost:8080/
```

Partial Read 확인:

```bash
nc localhost 8080
```

일부만 먼저 입력합니다.

```text
GET / HTTP/1.1
```

이후 나머지를 입력합니다.

```text
Host: localhost:8080

```

Timeout 확인:

```bash
nc localhost 8080
```

아무것도 입력하지 않고 30초 이상 기다리면 idle timeout 로그가 출력됩니다.

잘못된 요청 확인:

```text
HELLO THIS IS NOT HTTP

```

예상 응답:

```text
HTTP/1.1 400 Bad Request
```

큰 header 확인:

```text
HTTP/1.1 431 Request Header Fields Too Large
```

## 완료 기준

```text
HTTP/1.1 keep-alive 연결을 유지한다.
Connection: close 요청은 응답 후 닫는다.
요청이 나뉘어 들어와도 header 종료 전에는 처리하지 않는다.
응답이 일부만 쓰이면 OP_WRITE에서 이어서 쓴다.
Connection 객체가 read/write buffer와 상태를 가진다.
idle timeout으로 오래 쉬는 연결을 정리한다.
malformed request는 400으로 응답한다.
큰 header는 431로 응답한다.
```
