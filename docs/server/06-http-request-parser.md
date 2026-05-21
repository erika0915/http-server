# HTTP Request Parser

HTTP 요청 문자열을 `HttpRequest` 객체로 변환하는 첫 번째 Parser 단계입니다.

이번 단계의 목적은 URL별 라우팅이 아닙니다. 브라우저와 curl이 보낸 raw HTTP request를 코드에서 다루기 쉬운 객체로 바꾸는 것입니다.

관련 파일:

```text
src/main/java/httpserver/nio/http/HttpServer.java
src/main/java/httpserver/nio/http/request/HttpRequest.java
src/main/java/httpserver/nio/http/request/HttpRequestParser.java
```

아직 구현하지 않는 것:

- Router
- Static File Server
- Keep-Alive
- POST Body 완전 처리
- Content-Length 기반 Body 읽기 완성

## 구현 범위

- 기존 NIO HTTP 서버 구조 유지
- 클라이언트 요청을 `ByteBuffer`로 읽기
- Header 종료 지점인 `\r\n\r\n` 감지
- Request Line 파싱
- HTTP Method 파싱
- Path 파싱
- HTTP Version 파싱
- Header를 `Map<String, String>`으로 저장
- `HttpRequest` 클래스 생성
- `HttpRequestParser` 클래스 생성
- 파싱된 `HttpRequest`를 콘솔에 출력
- 응답은 기존처럼 `Hello NIO` 반환

## 실행 방법

IntelliJ에서 실행:

1. `HttpServer.java` 파일을 엽니다.
2. `public static void main(String[] args)` 왼쪽 실행 버튼을 누릅니다.
3. `Run 'HttpServer.main()'`을 선택합니다.

Gradle로 실행:

```bash
gradle run
```

또는 명시적으로:

```bash
gradle runHttpServer
```

JDK로 직접 실행:

```bash
javac -d build/classes/java/main src/main/java/httpserver/nio/http/request/HttpRequest.java src/main/java/httpserver/nio/http/request/HttpRequestParser.java src/main/java/httpserver/nio/http/HttpServer.java
java -cp build/classes/java/main httpserver.nio.http.HttpServer
```

## 테스트 방법

브라우저:

```text
http://localhost:8080/hello
```

curl:

```bash
curl localhost:8080/hello
```

헤더까지 확인:

```bash
curl -i localhost:8080/hello
```

nc:

```bash
printf 'GET /hello HTTP/1.1\r\nHost: localhost:8080\r\nUser-Agent: netcat-test\r\n\r\n' | nc localhost 8080
```

예상 파싱 결과:

```text
method  = GET
path    = /hello
version = HTTP/1.1
headers = {
  Host=localhost:8080
  User-Agent=netcat-test
}
body = ""
```

## 왜 HTTP Request를 객체로 변환하는가?

raw HTTP request는 하나의 긴 문자열입니다.

서버가 앞으로 라우팅, 파일 응답, 메서드 분기 같은 일을 하려면 문자열을 매번 직접 자르는 방식은 불편하고 실수하기 쉽습니다.

`HttpRequest` 객체로 바꾸면 다음처럼 의미 있는 값으로 다룰 수 있습니다.

```java
request.getMethod();
request.getPath();
request.getHeaders();
```

## Request Line 파싱 방식

Request Line은 요청의 첫 줄입니다.

```text
GET /hello HTTP/1.1
```

공백 기준으로 세 부분으로 나눕니다.

- method: `GET`
- path: `/hello`
- version: `HTTP/1.1`

세 부분이 아니면 잘못된 Request Line으로 보고 예외를 발생시킵니다.

## Header를 Map으로 저장하는 이유

Header는 이름과 값의 쌍입니다.

```text
Host: localhost:8080
User-Agent: curl/8.0
Accept: */*
```

`Map<String, String>`으로 저장하면 나중에 특정 Header를 이름으로 쉽게 찾을 수 있습니다.

```java
request.getHeaders().get("Host");
```

## Header 이름이 대소문자를 구분하지 않는 이유

HTTP Header 이름은 대소문자를 구분하지 않습니다.

즉 아래 세 이름은 같은 Header로 다뤄야 합니다.

```text
Host
host
HOST
```

이번 구현은 `TreeMap`에 `String.CASE_INSENSITIVE_ORDER`를 사용해서 대소문자와 무관하게 조회할 수 있게 했습니다.

## Body는 언제 필요한가?

Body는 클라이언트가 서버로 데이터를 보낼 때 사용됩니다.

대표적으로 `POST`, `PUT`, `PATCH` 요청에서 JSON, form data, 파일 업로드 같은 데이터가 body에 들어갑니다.

## GET 요청에는 왜 보통 Body가 없는가?

GET은 보통 리소스를 조회하기 위한 요청입니다.

조회 조건은 대개 path나 query string에 들어갑니다.

```text
GET /hello?name=nio HTTP/1.1
```

그래서 일반적인 GET 요청은 body 없이 header의 빈 줄에서 끝납니다.

## Parser를 서버 코드와 분리하는 이유

서버 코드는 네트워크 I/O를 담당하고, Parser는 문자열 분석을 담당합니다.

두 책임을 분리하면 다음 단계에서 Parser만 따로 개선하기 쉽습니다.

- Request Line 검증 강화
- Header 처리 개선
- Content-Length 기반 body 읽기
- 테스트 코드 추가

지금은 첫 번째 버전이므로 단순하게 유지합니다.
