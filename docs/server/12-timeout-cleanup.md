# Timeout / Cleanup

Step 12는 keep-alive 연결을 더 안정적으로 관리하는 단계입니다.

관련 파일:

```text
src/main/java/httpserver/nio/http/HttpServer.java
src/main/java/httpserver/nio/http/Connection.java
src/main/java/httpserver/nio/http/ConnectionState.java
src/main/java/httpserver/nio/http/response/HttpResponse.java
```

## 왜 timeout이 필요한가?

Keep-Alive는 하나의 TCP 연결을 여러 HTTP 요청에 재사용하게 해 줍니다.

하지만 클라이언트가 연결만 열어 두고 더 이상 요청을 보내지 않으면 서버는 그 연결의 `SocketChannel`, `SelectionKey`, `ByteBuffer`, `Connection` 객체를 계속 들고 있게 됩니다.

그래서 실제 서버는 idle timeout을 둡니다.

이번 서버의 기본 idle timeout은 30초입니다.

```text
30초 동안 read/write 활동이 없으면 연결 종료
```

## idle connection과 half-open connection

idle connection은 연결은 살아 있지만 한동안 데이터가 오가지 않는 연결입니다.

half-open connection은 한쪽에서는 연결이 살아 있다고 생각하지만, 반대쪽은 이미 끊겼거나 네트워크 문제로 응답하지 않는 상태입니다.

이런 연결을 계속 들고 있으면 서버 자원이 낭비됩니다.

## cleanup 흐름

Event Loop는 `selector.select(1000)`으로 최대 1초마다 깨어납니다.

깨어난 뒤 active connection 목록을 검사합니다.

```text
now - connection.lastActiveAt >= 30000
```

이면 timeout으로 판단하고 다음 작업을 합니다.

```text
SelectionKey cancel
Connection close
active connection 목록에서 제거
```

## malformed request

HTTP 요청 형식이 잘못되면 `400 Bad Request`를 반환합니다.

예:

```text
HELLO THIS IS NOT HTTP
```

HTTP 서버는 잘못된 요청을 그냥 예외로 터뜨리면 안 됩니다. 클라이언트에게 “요청 형식이 잘못됐다”는 HTTP 응답을 보내고 연결을 정리해야 합니다.

## header size limit

요청 Header가 너무 크면 `431 Request Header Fields Too Large`를 반환합니다.

이번 서버의 최대 Header 크기는 8KB입니다.

Header 크기 제한이 없으면 클라이언트가 매우 큰 Header를 보내서 서버 메모리를 오래 점유할 수 있습니다.

## SelectionKey cancel

`SelectionKey`는 Selector가 감시 중인 channel 등록 정보입니다.

연결을 닫을 때 key를 cancel하지 않으면 Selector가 이미 닫힌 channel을 계속 다루려고 할 수 있습니다.

그래서 close와 함께 key cancel이 필요합니다.

## close와 cleanup을 나누는 이유

`close`는 실제 연결을 닫는 동작입니다.

`cleanup`은 닫힌 연결을 active connection 목록에서 제거하고 서버가 더 이상 추적하지 않게 만드는 동작입니다.

실제 NGINX, Tomcat, Netty도 timeout과 connection cleanup을 수행합니다.

## 테스트

정상 요청:

```bash
curl -v http://localhost:8080/
```

idle timeout:

```bash
nc localhost 8080
```

아무것도 입력하지 않고 30초 이상 기다리면 서버 로그에 다음 형태가 출력됩니다.

```text
[conn-1] idle timeout after 30000ms
[conn-1] closed reason=idle-timeout
```

malformed request:

```text
HELLO THIS IS NOT HTTP

```

응답:

```text
HTTP/1.1 400 Bad Request
```

큰 header:

```text
GET / HTTP/1.1
Host: localhost:8080
X-Big: 아주 긴 값...

```

응답:

```text
HTTP/1.1 431 Request Header Fields Too Large
```
