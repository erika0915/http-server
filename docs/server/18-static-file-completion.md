# Static File Completion

Step 18은 Static File Server의 완성도를 높이는 단계입니다.

이전 단계에서는 `public` 디렉토리의 HTML, CSS, JS 파일을 읽어 응답하는 기본 정적 파일 서버를 만들었습니다.

이번 단계에서는 참고 repo의 방향에 맞춰 정적 파일 응답에 필요한 metadata와 캐시 검증, 디렉토리 요청 처리를 추가합니다.

## 목표

다음 기능을 하나의 정적 파일 서버 개선 step으로 묶어 구현합니다.

```text
Expanded MIME Types
Last-Modified
ETag
Conditional Request
Directory Listing
```

## 구현 내용

### 1. MIME 타입 확장

파일 확장자에 따라 더 다양한 `Content-Type`을 반환하도록 확장했습니다.

예시:

```text
.html -> text/html; charset=utf-8
.css  -> text/css; charset=utf-8
.js   -> application/javascript; charset=utf-8
.json -> application/json; charset=utf-8
.svg  -> image/svg+xml
.webp -> image/webp
.ico  -> image/x-icon
.pdf  -> application/pdf
```

### 2. Last-Modified

정적 파일의 마지막 수정 시간을 HTTP 날짜 형식으로 응답합니다.

```text
Last-Modified: Wed, 21 Oct 2015 07:28:00 GMT
```

### 3. ETag

파일 크기와 마지막 수정 시간을 기반으로 간단한 ETag를 생성합니다.

```text
ETag: "27d-19ab12cd34"
```

### 4. Conditional Request

클라이언트가 다음 header를 보내면 서버가 파일 변경 여부를 확인합니다.

```text
If-None-Match
If-Modified-Since
```

파일이 바뀌지 않았다면 body 없이 `304 Not Modified`를 반환합니다.

### 5. Directory Listing

요청 path가 디렉토리인 경우 다음 순서로 처리합니다.

```text
1. 디렉토리 안에 index.html이 있으면 index.html 반환
2. index.html이 없으면 파일 목록 HTML 생성
```

## 핵심 개념

Static File Server는 단순히 파일 byte를 읽어 보내는 것에서 끝나지 않습니다.

실제 HTTP 서버는 정적 파일 응답에 다음 정보를 함께 제공합니다.

```text
Content-Type
Content-Length
Last-Modified
ETag
```

그리고 클라이언트가 이미 가진 캐시를 재사용할 수 있도록 조건부 요청을 처리합니다.

```text
If-None-Match      -> ETag 기반 검증
If-Modified-Since  -> Last-Modified 기반 검증
```

## 코드 흐름

```text
Router.handle(request)
-> StaticFileHandler.handle(request)
-> 요청 path를 public root 기준 파일 경로로 변환
-> path traversal 검사
-> 파일이면 MIME/Last-Modified/ETag 계산
-> 조건부 요청이면 304 Not Modified 판단
-> 디렉토리이면 index.html 또는 directory listing 반환
```

## 실행 방법

서버를 실행합니다.

```bash
java -cp build/classes/java/main httpserver.nio.http.HttpServer
```

## 테스트 방법

MIME 타입 확인:

```bash
curl -I http://localhost:8080/
curl -I http://localhost:8080/style.css
curl -I http://localhost:8080/app.js
```

`Last-Modified`와 `ETag` 확인:

```bash
curl -i http://localhost:8080/
```

`ETag` 조건부 요청:

```bash
curl -i http://localhost:8080/ -H 'If-None-Match: "ETAG_VALUE"'
```

`Last-Modified` 조건부 요청:

```bash
curl -i http://localhost:8080/ -H 'If-Modified-Since: Wed, 21 Oct 2030 07:28:00 GMT'
```

디렉토리 리스팅 확인:

```bash
mkdir -p public/assets
touch public/assets/example.txt
curl -i http://localhost:8080/assets/
```

## 완료 기준

```text
주요 확장자에 대해 적절한 Content-Type을 반환한다.
정적 파일 응답에 Last-Modified header를 포함한다.
정적 파일 응답에 ETag header를 포함한다.
If-None-Match가 현재 ETag와 같으면 304 Not Modified를 반환한다.
If-Modified-Since 이후 파일이 변경되지 않았으면 304 Not Modified를 반환한다.
디렉토리 요청에 index.html이 있으면 index.html을 반환한다.
디렉토리 요청에 index.html이 없으면 파일 목록 HTML을 반환한다.
Path traversal 방지는 유지된다.
```
