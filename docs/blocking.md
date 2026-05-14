# Blocking Echo Server

Java 네트워크 기초와 TCP 흐름을 이해하기 위한 Blocking I/O Echo Server 문서입니다.

현재 구현은 HTTP를 다루지 않습니다. `ServerSocket`과 `Socket`을 사용해서 클라이언트가 보낸 문자열을 그대로 다시 돌려주는 가장 단순한 TCP 서버입니다.

## 구현 범위

이 문서가 설명하는 파일:

```text
src/main/java/dev/httpserver/blocking/BlockingEchoServer.java
```

구현된 내용:

- `ServerSocket`으로 TCP 서버 열기
- `localhost:8080`에서 클라이언트 연결 기다리기
- `accept()`로 클라이언트 연결 수락하기
- 클라이언트가 보낸 문자열을 그대로 다시 보내기
- 여러 줄 입력을 줄 단위로 처리하기
- 클라이언트 연결, 메시지 수신, 연결 종료 로그 출력하기
- Blocking I/O가 어디서 멈춰 기다리는지 코드로 확인하기

구현하지 않은 내용:

- HTTP 요청/응답 파싱
- NIO API
- `Selector`
- `SocketChannel`
- 외부 라이브러리 사용

## 기술 스택

- Language: Java 21
- Build System: Gradle
- External Libraries: 없음
- I/O Model: Blocking I/O

## 패키지 구조

```text
src/main/java
└── dev/httpserver
    └── blocking
        └── BlockingEchoServer.java
```


## 서버 실행 방법

IntelliJ에서 실행:

1. 프로젝트 폴더를 IntelliJ로 엽니다.
2. Gradle 프로젝트로 인식되면 JDK를 21로 설정합니다.
3. `BlockingEchoServer.java` 파일을 엽니다.
4. `public static void main(String[] args)` 왼쪽의 실행 버튼을 누릅니다.
5. `Run 'BlockingEchoServer.main()'`을 선택합니다.

`main()` 실행 버튼이 보이지 않으면 `src/main/java` 폴더를 우클릭한 뒤 `Mark Directory as` -> `Sources Root`를 선택합니다.

Gradle로 실행:

```bash
gradle run
```

JDK로 직접 실행:

```bash
javac -d build/classes/java/main src/main/java/dev/httpserver/blocking/BlockingEchoServer.java
java -cp build/classes/java/main dev.httpserver.blocking.BlockingEchoServer
```

서버가 정상 실행되면 다음과 비슷한 로그가 출력됩니다.

```text
[server] Blocking Echo Server started
[server] Listening on localhost:8080
[server] Test with: nc localhost 8080
```

## 테스트 방법

서버를 실행한 뒤 다른 터미널에서 접속합니다.

```bash
nc localhost 8080
```

입력 예:

```text
hello
network
```

응답 예:

```text
hello
network
```

`Ctrl+C` 또는 `Ctrl+D`로 클라이언트를 종료할 수 있습니다.

## 왜 Blocking I/O인가?

이 서버에서 blocking이 발생하는 대표 지점은 두 곳입니다.

```java
Socket clientSocket = serverSocket.accept();
```

`accept()`는 클라이언트가 연결될 때까지 현재 스레드를 멈춰 기다립니다. 연결 요청이 없으면 다음 줄로 진행하지 않습니다.

```java
while ((line = reader.readLine()) != null) {
    ...
}
```

`readLine()`은 클라이언트가 한 줄을 보낼 때까지 현재 스레드를 멈춰 기다립니다. `nc`에서 글자를 입력하고 Enter를 누르면 그제야 한 줄이 서버로 전달되고 `readLine()`이 반환됩니다.

현재 코드는 한 클라이언트를 처리하는 동안 다음 `accept()`로 돌아가지 않습니다. 그래서 첫 번째 클라이언트가 연결만 해두고 아무것도 보내지 않으면 서버 스레드는 `readLine()`에서 기다리고, 다른 클라이언트 처리는 지연됩니다.

## InputStream / OutputStream 동작 원리

TCP 소켓은 문자열을 직접 주고받지 않습니다. 실제 네트워크 데이터는 바이트 흐름입니다.

- `InputStream`: 클라이언트에서 서버로 들어오는 바이트를 읽습니다.
- `OutputStream`: 서버에서 클라이언트로 나가는 바이트를 씁니다.
- `InputStreamReader`: 바이트를 문자로 변환합니다.
- `OutputStreamWriter`: 문자를 바이트로 변환합니다.
- `BufferedReader`: `readLine()`으로 줄 단위 입력을 쉽게 읽습니다.
- `BufferedWriter`: 출력 문자를 버퍼에 모았다가 `flush()` 시점에 내보냅니다.

Echo Server는 `InputStream`에서 읽은 내용을 `OutputStream`으로 다시 쓰는 구조입니다.
