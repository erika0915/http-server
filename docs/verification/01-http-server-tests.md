# HTTP Server Tests

검증과 성능 개선 1번은 JUnit 5 기반 테스트 체계를 추가하는 단계입니다.

## 목표

서버 구현 phase에서 만든 기능이 계속 정상 동작하는지 자동으로 확인할 수 있게 합니다.

이번 단계에서는 다음 영역을 테스트합니다.

```text
HttpRequestParser
MimeTypes
StaticFileHandler
Parser -> Router -> HttpResponse 흐름
```

## 구현 내용

### 1. JUnit 5 설정

`build.gradle`에 JUnit 5 의존성과 `useJUnitPlatform()` 설정을 추가했습니다.

```text
testImplementation platform("org.junit:junit-bom:5.10.3")
testImplementation "org.junit.jupiter:junit-jupiter"
```

### 2. Parser Unit Tests

대상:

```text
src/test/java/httpserver/nio/http/request/HttpRequestParserTest.java
```

검증 내용:

```text
정상 GET 요청 파싱
잘못된 Request Line 거부
HTTP/1.1 Host header 필수 검증
Content-Length body 파싱
잘못된 Content-Length 거부
chunked body 파싱
HEAD method 파싱
```

### 3. Static File Tests

대상:

```text
src/test/java/httpserver/nio/http/staticfile/MimeTypesTest.java
src/test/java/httpserver/nio/http/staticfile/StaticFileHandlerTest.java
```

검증 내용:

```text
확장자별 Content-Type
정적 파일 metadata header
ETag 기반 304 Not Modified
path traversal 차단
directory listing
```

### 4. Integration Tests

대상:

```text
src/test/java/httpserver/nio/http/integration/HttpServerFlowTest.java
```

검증 내용:

```text
GET / 요청 흐름
HEAD / 요청 흐름
POST body 파싱 후 405 응답
unknown path 404 응답
```

실제 포트를 여는 end-to-end 테스트는 아직 추가하지 않았습니다.

이번 단계에서는 테스트가 안정적으로 돌도록 `Parser -> Router -> HttpResponse` 흐름을 검증합니다.

## 실행 방법

Gradle이 설치되어 있으면 다음 명령으로 실행합니다.

```bash
gradle test
```

또는 Gradle wrapper가 추가된 이후에는 다음 명령을 사용할 수 있습니다.

```bash
./gradlew test
```

현재 작업 환경에는 `gradle` 명령이 없어, 검증 시에는 JUnit Console Standalone을 사용해 테스트를 실행했습니다.

```bash
mvn -q dependency:get -Dartifact=org.junit.platform:junit-platform-console-standalone:1.10.3
javac -cp build/classes/java/main:$HOME/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.10.3/junit-platform-console-standalone-1.10.3.jar \
  -d build/classes/java/test-manual \
  src/test/java/httpserver/nio/http/request/*.java \
  src/test/java/httpserver/nio/http/staticfile/*.java \
  src/test/java/httpserver/nio/http/integration/*.java
java -jar $HOME/.m2/repository/org/junit/platform/junit-platform-console-standalone/1.10.3/junit-platform-console-standalone-1.10.3.jar \
  --class-path build/classes/java/main:build/classes/java/test-manual \
  --scan-class-path
```

검증 결과:

```text
18 tests successful
0 tests failed
```

## 완료 기준

```text
JUnit 5 테스트 환경이 구성된다.
Parser unit test가 통과한다.
Static file test가 통과한다.
Integration flow test가 통과한다.
gradle test가 성공한다.
```
