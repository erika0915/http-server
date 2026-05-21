# AGENTS.md

이 파일은 Codex가 이 프로젝트에서 작업할 때 따라야 하는 프로젝트 전용 규칙입니다.

## 프로젝트 목표

이 프로젝트는 Java NIO를 사용해 HTTP 서버를 저수준에서 직접 구현하는 학습 프로젝트입니다.

핵심 목표는 다음과 같습니다.

- Java NIO의 `Selector`, `ServerSocketChannel`, `SocketChannel`, `ByteBuffer` 동작 이해
- TCP 연결과 HTTP 요청/응답 흐름 이해
- HTTP/1.1 서버의 구조를 단계적으로 구현
- 참고 프로젝트 수준에 가까워지기 위해 HTTP 프로토콜 처리, 정적 파일 서빙, 테스트, benchmark를 순서대로 확장

## 기본 작업 원칙

- Java 21 기준으로 작업한다.
- Gradle 프로젝트 구조를 유지한다.
- 외부 라이브러리는 꼭 필요한 경우가 아니면 사용하지 않는다.
- 학습용 프로젝트이므로 코드 가독성을 우선한다.
- 지나치게 복잡한 추상화는 피한다.
- 기존 패키지 구조와 코드 스타일을 우선적으로 따른다.
- 사용자가 요청하지 않은 기능을 임의로 크게 확장하지 않는다.
- 기존 사용자 변경사항을 되돌리지 않는다.

## Step 진행 규칙

하나의 step을 진행할 때는 반드시 다음을 함께 처리한다.

1. 코드 구현
2. 관련 docs 문서 작성
3. README 진행 상황 표 업데이트
4. 실행 방법 정리
5. 검증 방법 정리
6. 이번 step의 학습 목표 정리
7. 다음 step으로 넘어가기 전 완료 기준 정리

즉, step 하나는 단순히 코드만 추가하는 작업이 아니라 다음 형태의 학습 단위여야 한다.

```text
Step = Code + Docs + README + Verification
```

## Docs 작성 규칙

각 step의 문서는 `docs` 디렉토리 아래에 숫자 기반 파일명으로 작성한다.

예시:

```text
docs/14-http-request-validation.md
docs/15-host-header-validation.md
docs/16-content-length-body.md
```

문서에는 최소한 다음 내용을 포함한다.

- 목표
- 구현 내용
- 핵심 개념
- 코드 흐름
- 실행 방법
- 테스트 방법
- 완료 기준

문서는 너무 장황하지 않게 작성하되, 나중에 다시 봤을 때 왜 이 step을 했는지 이해할 수 있어야 한다.

## README 작성 규칙

README는 프로젝트 전체 소개와 진행 상황 요약만 담는다.

README의 진행 상황 표는 다음 형식을 유지한다.

```text
숫자 / 이름 / 학습 목표 / 설명 / Docs
```

README에는 긴 설명을 넣지 않는다.

상세 설명은 각 `docs/*.md` 파일에 작성한다.

## 현재 진행 방향

참고 프로젝트처럼 더 완성도 있는 HTTP 서버에 가까워지기 위해 HTTP/1.1 기능 완성도를 먼저 높인다.

권장 진행 순서는 다음과 같다.

```text
14 HTTP/1.1 Request Validation
15 Content-Length Body Handling
16 Transfer-Encoding Chunked
17 HEAD Method Support
18 Expanded MIME Types
19 Last-Modified
20 ETag
21 Conditional Request
22 Directory Listing
23 Parser / Server Unit Tests
24 Benchmark Environment
25 Benchmark Report
```

## 구현 시 주의사항

- 아직 구현하지 않은 기능을 README에 완료된 것처럼 쓰지 않는다.
- benchmark 수치는 실제 측정하지 않았다면 확정적으로 쓰지 않는다.
- HTTP 스펙 관련 기능은 가능한 작은 step으로 나누어 구현한다.
- 각 step은 독립적으로 이해 가능해야 한다.
- 서버 동작을 바꾸는 경우 docs에 변경 전/후 흐름을 기록한다.

## 검증 원칙

가능하면 각 step마다 다음 중 하나 이상으로 검증한다.

```text
curl
nc
브라우저
javac
Gradle
간단한 수동 테스트
단위 테스트
```

검증을 실행하지 못한 경우에는 그 이유를 명확히 남긴다.

## 커밋 메시지 원칙

커밋 메시지는 step 단위로 작성한다.

예시:

```text
feat: add HTTP request validation
feat: validate required Host header
feat: support Content-Length request body
docs: document HTTP request validation step
```
