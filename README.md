# Java NIO HTTP 저수준 Server

Java NIO의 `Selector`, `SocketChannel`, `ServerSocketChannel`, `ByteBuffer`를 직접 사용해 HTTP 서버의 내부 동작을 단계적으로 구현하는 프로젝트입니다.

프레임워크나 웹 서버 라이브러리에 의존하지 않고, TCP 연결 수락부터 HTTP 요청 파싱, 응답 생성, 정적 파일 서빙, Keep-Alive, connection 상태 관리, Multi EventLoop 구조까지 저수준에서 직접 다루는 것을 목표로 합니다.

## 진행 상황

| 숫자 | 이름 | 학습 목표 | 설명 | Docs |
| --- | --- | --- | --- | --- |
| 1 | Blocking Echo Server | TCP 연결 흐름 이해 | `ServerSocket` 기반으로 클라이언트가 보낸 문자열을 그대로 돌려주는 서버를 구현했습니다. | [docs/01-blocking.md](docs/01-blocking.md) |
| 2 | NIO Echo Server | Non-blocking I/O 이해 | `Selector`와 `SocketChannel`을 사용해 하나의 스레드에서 여러 연결을 감시하는 Echo Server를 구현했습니다. | [docs/02-nio.md](docs/02-nio.md) |
| 3 | ByteBuffer Experiment | NIO 버퍼 동작 이해 | `ByteBuffer`의 `position`, `limit`, `capacity` 변화와 `flip()`, `clear()`, `compact()` 동작을 실험했습니다. | [docs/03-byte-buffer.md](docs/03-byte-buffer.md) |
| 4 | Minimal HTTP Server | HTTP 응답 형식 이해 | 브라우저와 curl이 인식할 수 있는 최소 HTTP 응답을 반환했습니다. | [docs/04-minimal-http.md](docs/04-minimal-http.md) |
| 5 | HTTP Request Observation | 실제 HTTP 요청 구조 관찰 | 브라우저, curl, nc가 보내는 raw HTTP 요청과 Header 구조를 콘솔에 출력했습니다. | [docs/05-http-request-observation.md](docs/05-http-request-observation.md) |
| 6 | HttpRequest Parser | 문자열 요청을 객체로 변환 | raw HTTP request를 `HttpRequest` 객체로 파싱하는 첫 번째 parser를 구현했습니다. | [docs/06-http-request-parser.md](docs/06-http-request-parser.md) |
| 7 | Router / HttpResponse | 요청별 응답 분기 이해 | method와 path에 따라 다른 응답을 반환하는 `Router`와 `HttpResponse`를 구현했습니다. | [docs/07-router.md](docs/07-router.md) |
| 8 | Static File Server | 파일을 HTTP 응답으로 제공 | `public` 디렉토리의 HTML, CSS, JS 파일을 읽어 브라우저에 반환했습니다. | [docs/08-static-file-server.md](docs/08-static-file-server.md) |
| 9 | Keep-Alive | TCP 연결 재사용 이해 | 하나의 TCP 연결에서 여러 HTTP 요청을 처리할 수 있도록 Keep-Alive를 구현했습니다. | [docs/09-keep-alive.md](docs/09-keep-alive.md) |
| 10 | Partial Read / Partial Write | TCP byte stream 처리 이해 | 요청이 나뉘어 들어오거나 응답이 일부만 전송되는 상황을 처리했습니다. | [docs/10-partial-read-write.md](docs/10-partial-read-write.md) |
| 11 | Connection State Management | 연결별 상태 관리 이해 | `Connection` 객체가 buffer, 상태, keep-alive 여부, lifecycle을 관리하도록 리팩토링했습니다. | [docs/11-connection-state.md](docs/11-connection-state.md) |
| 12 | Timeout / Cleanup | 안정적인 연결 정리 이해 | idle timeout, 잘못된 요청 처리, header size 제한, connection cleanup을 구현했습니다. | [docs/12-timeout-cleanup.md](docs/12-timeout-cleanup.md) |
| 13 | Multi EventLoop | Reactor 구조 이해 | accept 전용 Boss EventLoop와 read/write 전용 Worker EventLoop를 분리했습니다. | [docs/13-multi-event-loop.md](docs/13-multi-event-loop.md) |
| 14 | HTTP/1.1 Request Validation | 요청 라인 검증 이해 | HTTP method, path, version 형식을 검증하고 잘못된 요청은 `400 Bad Request`로 응답하도록 구현했습니다. | [docs/14-http-request-validation.md](docs/14-http-request-validation.md) |
