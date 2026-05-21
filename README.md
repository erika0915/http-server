# Java NIO HTTP 저수준 Server

Java NIO의 `Selector`, `SocketChannel`, `ServerSocketChannel`, `ByteBuffer`를 직접 사용해 HTTP 서버의 내부 동작을 단계적으로 구현하는 프로젝트입니다.

프레임워크나 웹 서버 라이브러리에 의존하지 않고, TCP 연결 수락부터 HTTP 요청 파싱, 응답 생성, 정적 파일 서빙, Keep-Alive, connection 상태 관리, Multi EventLoop 구조까지 저수준에서 직접 다루는 것을 목표로 합니다.

## 서버 구현

| 숫자 | 이름 | 학습 목표 | 설명 | Docs |
| --- | --- | --- | --- | --- |
| 1 | Blocking Echo Server | TCP 연결 흐름 이해 | `ServerSocket` 기반으로 클라이언트가 보낸 문자열을 그대로 돌려주는 서버를 구현했습니다. | [docs/server/01-blocking.md](docs/server/01-blocking.md) |
| 2 | NIO Echo Server | Non-blocking I/O 이해 | `Selector`와 `SocketChannel`을 사용해 하나의 스레드에서 여러 연결을 감시하는 Echo Server를 구현했습니다. | [docs/server/02-nio.md](docs/server/02-nio.md) |
| 3 | ByteBuffer Experiment | NIO 버퍼 동작 이해 | `ByteBuffer`의 `position`, `limit`, `capacity` 변화와 `flip()`, `clear()`, `compact()` 동작을 실험했습니다. | [docs/server/03-byte-buffer.md](docs/server/03-byte-buffer.md) |
| 4 | Minimal HTTP Server | HTTP 응답 형식 이해 | 브라우저와 curl이 인식할 수 있는 최소 HTTP 응답을 반환했습니다. | [docs/server/04-minimal-http.md](docs/server/04-minimal-http.md) |
| 5 | HTTP Request Observation | 실제 HTTP 요청 구조 관찰 | 브라우저, curl, nc가 보내는 raw HTTP 요청과 Header 구조를 콘솔에 출력했습니다. | [docs/server/05-http-request-observation.md](docs/server/05-http-request-observation.md) |
| 6 | HttpRequest Parser | 문자열 요청을 객체로 변환 | raw HTTP request를 `HttpRequest` 객체로 파싱하는 첫 번째 parser를 구현했습니다. | [docs/server/06-http-request-parser.md](docs/server/06-http-request-parser.md) |
| 7 | Router / HttpResponse | 요청별 응답 분기 이해 | method와 path에 따라 다른 응답을 반환하는 `Router`와 `HttpResponse`를 구현했습니다. | [docs/server/07-router.md](docs/server/07-router.md) |
| 8 | Static File Server | 파일 응답과 캐시 검증 이해 | `public` 디렉토리의 파일을 제공하고 MIME, `ETag`, `Last-Modified`, 조건부 요청, 디렉토리 요청을 처리했습니다. | [docs/server/08-static-file-server.md](docs/server/08-static-file-server.md) |
| 9 | Connection Lifecycle | 연결 재사용과 상태 관리 이해 | Keep-Alive, Partial Read/Write, `Connection` 상태 머신, timeout/cleanup을 하나의 연결 lifecycle로 정리했습니다. | [docs/server/09-connection-lifecycle.md](docs/server/09-connection-lifecycle.md) |
| 10 | Multi EventLoop | Reactor 구조 이해 | accept 전용 Boss EventLoop와 read/write 전용 Worker EventLoop를 분리했습니다. | [docs/server/10-multi-event-loop.md](docs/server/10-multi-event-loop.md) |
| 11 | HTTP/1.1 Protocol Handling | 요청 검증과 body 처리 이해 | Request Line/Host 검증, `Content-Length`, chunked body, `HEAD` method를 처리했습니다. | [docs/server/11-http11-protocol-handling.md](docs/server/11-http11-protocol-handling.md) |

## 검증과 성능 개선

| 숫자 | 이름 | 목표 | 설명 | Docs |
| --- | --- | --- | --- | --- |
| 1 | HTTP Server Tests | 동작 검증 체계 구축 | Parser, Static File, Server Integration 테스트를 추가해 서버 기능을 검증합니다. | [docs/verification/01-http-server-tests.md](docs/verification/01-http-server-tests.md) |
| 2 | Logging Control | 성능 측정 준비 | 학습용 상세 로그와 benchmark용 최소 로그를 분리할 수 있도록 로그 레벨을 제어합니다. | [docs/verification/02-logging-control.md](docs/verification/02-logging-control.md) |
| 3 | Benchmark Environment | 부하 테스트 환경 구축 | k6, Prometheus, Grafana 기반으로 서버 성능을 관찰할 수 있는 환경을 구성했습니다. | [docs/verification/03-benchmark-environment.md](docs/verification/03-benchmark-environment.md) |
| 4 | Benchmark Report | 성능 결과 분석 | RPS, latency, error rate, worker 수에 따른 차이를 측정하고 기록했습니다. | [docs/verification/04-benchmark-report.md](docs/verification/04-benchmark-report.md) |
