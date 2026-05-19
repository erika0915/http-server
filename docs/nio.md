# NIO Echo Server

Java NIO의 핵심 개념인 `Selector`, `ServerSocketChannel`, `SocketChannel`, `ByteBuffer`를 학습하기 위한 Echo Server 문서입니다.

현재 구현은 HTTP를 다루지 않습니다. 클라이언트가 TCP 연결로 보낸 바이트를 그대로 다시 돌려주는 Echo Server입니다.

## 구현 범위

이 문서가 설명하는 파일:

```text
src/main/java/dev/httpserver/nio/NioEchoServer.java
```

구현된 내용:

- `ServerSocketChannel` 기반 TCP 서버
- `localhost:8080`에서 실행
- `configureBlocking(false)` 사용
- `Selector` 사용
- `OP_ACCEPT` 이벤트 처리
- `OP_READ` 이벤트 처리
- 클라이언트가 보낸 데이터를 그대로 다시 `write`
- 클라이언트 연결/종료 로그 출력
- 클라이언트가 연결을 끊으면 channel close
- `SelectionKey` 처리 후 `selectedKeys`에서 제거
- `ByteBuffer` 사용

구현하지 않은 내용:

- HTTP 응답 형식
- HTTP Request Parser
- Router
- 외부 라이브러리 사용

## 패키지 구조

```text
src/main/java
└── dev/httpserver
    └── nio
        └── NioEchoServer.java
```

현재 패키지:

```java
package dev.httpserver.nio;
```

## 서버 실행 방법

IntelliJ에서 실행:

1. `NioEchoServer.java` 파일을 엽니다.
2. `public static void main(String[] args)` 왼쪽의 실행 버튼을 누릅니다.
3. `Run 'NioEchoServer.main()'`을 선택합니다.

`main()` 실행 버튼이 보이지 않으면 `src/main/java` 폴더를 우클릭한 뒤 `Mark Directory as` -> `Sources Root`를 선택합니다.

Gradle로 실행:

```bash
gradle run
```

또는 명시적으로:

```bash
gradle runNioEchoServer
```

JDK로 직접 실행:

```bash
javac -d build/classes/java/main src/main/java/dev/httpserver/nio/NioEchoServer.java
java -cp build/classes/java/main dev.httpserver.nio.NioEchoServer
```

## 테스트 방법

서버를 실행한 뒤 다른 터미널에서 접속합니다.

```bash
nc localhost 8080
```

입력 예:

```text
hello
nio
```

응답 예:

```text
hello
nio
```

여러 클라이언트 테스트:

1. 서버 터미널 하나를 켜둡니다.
2. 새 터미널을 여러 개 엽니다.
3. 각 터미널에서 `nc localhost 8080`을 실행합니다.
4. 각 클라이언트에서 문자열을 입력합니다.

NIO 서버는 하나의 서버 스레드에서 여러 클라이언트 채널의 준비 상태를 번갈아 처리할 수 있습니다.

## Blocking Echo Server와 NIO Echo Server의 차이

Blocking Echo Server는 `accept()`나 `readLine()`을 호출한 스레드가 직접 멈춰 기다립니다. 한 클라이언트가 연결된 채로 데이터를 보내지 않으면 서버는 그 클라이언트를 기다리느라 다음 작업으로 넘어가지 못할 수 있습니다.

NIO Echo Server는 채널을 Non-Blocking 모드로 설정하고 `Selector`에 등록합니다. 서버 스레드는 특정 클라이언트 하나를 계속 기다리지 않고, 준비된 이벤트가 있는 채널만 골라 처리합니다.

## Selector가 하는 일

`Selector`는 여러 채널의 이벤트를 한 곳에서 감시합니다.

서버는 다음과 같은 질문을 Selector에게 맡깁니다.

- 새 클라이언트 연결을 받을 준비가 된 서버 채널이 있는가?
- 읽을 데이터가 도착한 클라이언트 채널이 있는가?

`selector.select()`는 등록된 채널 중 하나라도 준비될 때까지 기다렸다가, 준비된 이벤트들을 `selectedKeys()`로 알려줍니다.

## OP_ACCEPT와 OP_READ

`OP_ACCEPT`는 서버 채널에 새 클라이언트 연결 요청이 도착했고, `accept()`를 호출할 수 있다는 뜻입니다.

```java
serverChannel.register(selector, SelectionKey.OP_ACCEPT);
```

`OP_READ`는 클라이언트 채널에 읽을 데이터가 도착했고, `read(buffer)`를 호출할 수 있다는 뜻입니다.

```java
clientChannel.register(selector, SelectionKey.OP_READ);
```

## ByteBuffer의 flip()과 clear()

`ByteBuffer`는 내부적으로 `position`, `limit`, `capacity` 값을 가지고 있습니다.

`channel.read(buffer)`를 호출하면 네트워크에서 읽은 바이트가 buffer에 쓰입니다. 이때 buffer는 write mode처럼 사용됩니다.

```java
int bytesRead = clientChannel.read(buffer);
```

그 다음 같은 buffer를 `channel.write(buffer)`에 넘기려면, 방금 쓴 데이터를 처음부터 읽을 수 있어야 합니다. 이때 `flip()`을 호출합니다.

```java
buffer.flip();
```

`flip()`은 현재 `position`을 `limit`으로 바꾸고, `position`을 0으로 되돌립니다.

다시 buffer에 데이터를 쓰려면 `clear()`로 write mode 상태로 되돌립니다.

```java
buffer.clear();
```

`clear()`는 데이터를 지운다는 의미라기보다, 다음 쓰기를 위해 `position`과 `limit`을 초기화하는 동작에 가깝습니다.

## channel.read(buffer)가 -1을 반환하는 경우

`channel.read(buffer)`가 `-1`을 반환하면 클라이언트가 연결을 정상 종료했다는 뜻입니다.

예를 들어 `nc` 클라이언트에서 `Ctrl+D`를 누르거나 터미널을 종료하면 서버는 더 이상 읽을 데이터가 없고 연결도 끝났다는 신호를 받습니다. 이때 서버는 `SelectionKey`를 cancel하고 `SocketChannel`을 close해야 합니다.

## 왜 하나의 스레드로 여러 클라이언트를 처리할 수 있는가?

NIO에서는 채널을 Non-Blocking 모드로 설정합니다.

```java
serverChannel.configureBlocking(false);
clientChannel.configureBlocking(false);
```

Non-Blocking 채널은 준비되지 않은 작업 때문에 스레드를 오래 붙잡지 않습니다. 그리고 `Selector`가 여러 채널의 준비 상태를 모아서 알려줍니다.

그래서 서버 스레드는 다음 흐름을 반복할 수 있습니다.

1. `selector.select()`로 준비된 이벤트를 기다립니다.
2. `selectedKeys()`에서 준비된 채널 목록을 가져옵니다.
3. accept 가능한 채널은 연결을 수락합니다.
4. read 가능한 채널은 데이터를 읽고 echo합니다.
5. 처리한 key는 제거하고 다음 이벤트를 기다립니다.

이 구조 덕분에 클라이언트마다 스레드를 하나씩 만들지 않아도 여러 연결을 번갈아 처리할 수 있습니다.
