# Static File Server

정적 파일 서버는 디스크에 있는 HTML, CSS, JS, 이미지 파일을 읽어서 HTTP 응답으로 반환하는 서버입니다.

이 문서는 처음 만든 정적 파일 서버와 이후 완성도 개선 작업을 함께 정리합니다.

`public` 디렉토리를 정적 파일 root로 사용합니다.

```text
public/
├── index.html
├── style.css
└── app.js
```

관련 파일:

```text
src/main/java/httpserver/nio/http/router/Router.java
src/main/java/httpserver/nio/http/response/HttpResponse.java
src/main/java/httpserver/nio/http/staticfile/StaticFileHandler.java
src/main/java/httpserver/nio/http/staticfile/MimeTypes.java
```

## 목표

브라우저가 실제 HTML 페이지를 렌더링할 수 있도록 서버가 파일을 읽어 HTTP 응답으로 반환합니다.

단순 파일 반환에서 끝나지 않고, 실제 정적 파일 서버에 필요한 metadata와 캐시 검증도 함께 처리합니다.

## 구현 내용

기본 요청 흐름:

```text
GET /                  -> public/index.html 반환
GET /style.css         -> CSS 파일 반환
GET /app.js            -> JavaScript 파일 반환
GET /unknown           -> 404 Not Found
GET /../../etc/passwd  -> public 밖 접근 차단
```

정적 파일 응답에 포함하는 정보:

```text
Content-Type
Content-Length
Last-Modified
ETag
```

조건부 요청도 처리합니다.

```text
If-None-Match
If-Modified-Since
```

파일이 변경되지 않았다면 body 없이 `304 Not Modified`를 반환합니다.

## MIME Type

`Content-Type`은 브라우저가 body를 어떻게 해석해야 하는지 알려줍니다.

예시:

```text
.html -> text/html; charset=utf-8
.css  -> text/css; charset=utf-8
.js   -> application/javascript; charset=utf-8
.json -> application/json; charset=utf-8
.svg  -> image/svg+xml
.png  -> image/png
.jpg  -> image/jpeg
.webp -> image/webp
.ico  -> image/x-icon
.pdf  -> application/pdf
그 외 -> application/octet-stream
```

HTML은 문서로 파싱해야 하므로 `text/html`이 필요합니다.

JavaScript는 실행 가능한 스크립트로 처리해야 하므로 `application/javascript`를 사용합니다.

## Content-Length

파일 응답에서는 `Content-Length`를 파일의 byte 길이로 계산해야 합니다.

문자 수와 byte 수는 다를 수 있고, 이미지 같은 바이너리 파일은 문자열 길이로 계산할 수도 없습니다.

## Last-Modified / ETag

`Last-Modified`는 파일의 마지막 수정 시간입니다.

```text
Last-Modified: Wed, 21 Oct 2015 07:28:00 GMT
```

`ETag`는 파일이 바뀌었는지 확인하기 위한 식별자입니다.

이번 구현은 파일 크기와 마지막 수정 시간을 기반으로 간단한 ETag를 생성합니다.

```text
ETag: "27d-19ab12cd34"
```

## Conditional Request

브라우저나 curl이 이미 파일을 가지고 있으면 다음 header로 캐시 검증을 요청할 수 있습니다.

```text
If-None-Match
If-Modified-Since
```

파일이 바뀌지 않았다면 서버는 body 없이 다음 응답을 반환합니다.

```text
HTTP/1.1 304 Not Modified
```

이렇게 하면 같은 파일을 다시 내려받지 않아도 됩니다.

## Directory Request

요청 path가 디렉토리인 경우 다음 순서로 처리합니다.

```text
1. 디렉토리 안에 index.html이 있으면 index.html 반환
2. index.html이 없으면 파일 목록 HTML 생성
```

## Path Traversal 방지

Path Traversal은 사용자가 `../../` 같은 경로를 사용해서 서버의 공개 디렉토리 밖 파일에 접근하려는 공격입니다.

예:

```text
GET /../../etc/passwd
```

이번 구현은 다음 방식으로 막습니다.

```java
Path root = Paths.get("public").toAbsolutePath().normalize();
Path filePath = root.resolve(relativePath).normalize();
filePath.startsWith(root)
```

`normalize()`는 경로 안의 `.`과 `..`을 정리합니다.

그 결과가 `public` root 밖이면 요청을 차단합니다.

## 코드 흐름

```text
Router.handle(request)
-> StaticFileHandler.handle(request)
-> 요청 path를 public root 기준 파일 경로로 변환
-> path traversal 검사
-> 디렉토리이면 index.html 또는 directory listing 처리
-> 파일이면 MIME / Last-Modified / ETag 계산
-> 조건부 요청이면 304 Not Modified 판단
-> 파일 byte를 HttpResponse body로 반환
```

## 테스트 방법

브라우저:

```text
http://localhost:8080/
```

curl:

```bash
curl -v http://localhost:8080/
curl -v http://localhost:8080/style.css
curl -v http://localhost:8080/app.js
```

Header 확인:

```bash
curl -I http://localhost:8080/
curl -I http://localhost:8080/style.css
curl -I http://localhost:8080/app.js
```

Path Traversal 테스트:

```bash
curl -v http://localhost:8080/../../etc/passwd
```

조건부 요청 테스트:

```bash
curl -i http://localhost:8080/
curl -i http://localhost:8080/ -H 'If-None-Match: "ETAG_VALUE"'
curl -i http://localhost:8080/ -H 'If-Modified-Since: Wed, 21 Oct 2030 07:28:00 GMT'
```

디렉토리 리스팅 테스트:

```bash
mkdir -p public/assets
touch public/assets/example.txt
curl -i http://localhost:8080/assets/
```

## 실제 웹 서버

NGINX와 Apache 같은 실제 웹 서버도 정적 파일 서빙을 수행합니다.

요청 path를 파일 path로 매핑하고, 파일을 읽고, MIME Type과 Content-Length를 설정해서 응답하는 큰 흐름은 같습니다.

## 완료 기준

```text
public/index.html, style.css, app.js를 반환한다.
주요 확장자에 적절한 Content-Type을 반환한다.
Content-Length를 byte 기준으로 계산한다.
Last-Modified와 ETag를 반환한다.
If-None-Match가 현재 ETag와 같으면 304 Not Modified를 반환한다.
If-Modified-Since 이후 파일이 변경되지 않았으면 304 Not Modified를 반환한다.
디렉토리 요청에 index.html이 있으면 index.html을 반환한다.
디렉토리 요청에 index.html이 없으면 파일 목록 HTML을 반환한다.
Path traversal 방지는 유지된다.
```
