# Static File Server

정적 파일 서버는 디스크에 있는 HTML, CSS, JS, 이미지 파일을 읽어서 HTTP 응답으로 반환하는 서버입니다.

이번 단계에서는 `public` 디렉토리를 정적 파일 root로 사용합니다.

```text
public/
├── index.html
├── style.css
└── app.js
```

관련 파일:

```text
src/main/java/httpserver/nio/http/HttpServer.java
src/main/java/httpserver/nio/http/router/Router.java
src/main/java/httpserver/nio/http/response/HttpResponse.java
src/main/java/httpserver/nio/http/staticfile/StaticFileHandler.java
src/main/java/httpserver/nio/http/staticfile/MimeTypes.java
```

## 실행 방법

IntelliJ에서 `HttpServer.main()`을 실행하거나 Gradle을 사용합니다.

```bash
gradle run
```

## 테스트

브라우저:

```text
http://localhost:8080/
```

브라우저는 먼저 `/`를 요청해서 `index.html`을 받고, HTML 안의 링크를 보고 추가로 `/style.css`, `/app.js`를 요청합니다.

curl:

```bash
curl -v http://localhost:8080/
curl -v http://localhost:8080/style.css
curl -v http://localhost:8080/app.js
```

없는 파일:

```bash
curl -v http://localhost:8080/unknown
```

Path Traversal 테스트:

```bash
curl -v http://localhost:8080/../../etc/passwd
```

응답은 `404 Not Found`여야 합니다.

## MIME Type

`Content-Type`은 브라우저가 body를 어떻게 해석해야 하는지 알려줍니다.

- `.html` -> `text/html`
- `.css` -> `text/css`
- `.js` -> `application/javascript`
- `.json` -> `application/json`
- `.png` -> `image/png`
- `.jpg`, `.jpeg` -> `image/jpeg`
- 그 외 -> `application/octet-stream`

HTML은 문서로 파싱해야 하므로 `text/html`이 필요합니다.

JavaScript는 실행 가능한 스크립트로 처리해야 하므로 `application/javascript`를 사용합니다.

JSON은 데이터 형식이므로 `application/json`을 사용합니다.

## Content-Length

파일 응답에서는 `Content-Length`를 파일의 byte 길이로 계산해야 합니다.

문자 수와 byte 수는 다를 수 있고, 이미지 같은 바이너리 파일은 문자열 길이로 계산할 수도 없습니다.

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

## 브라우저와 curl 차이

curl은 요청한 URL 하나만 가져옵니다.

브라우저는 HTML을 받은 뒤 그 안에 있는 CSS, JS, 이미지 링크를 해석해서 추가 요청을 보냅니다.

Chrome 개발자도구 Network 탭을 열면 `/`, `/style.css`, `/app.js` 요청을 따로 확인할 수 있습니다.

## 이미지 테스트

`public/sample.png` 같은 이미지 파일을 추가한 뒤 HTML에 다음처럼 넣으면 됩니다.

```html
<img src="/sample.png" alt="sample">
```

그 다음 브라우저 Network 탭에서 `/sample.png` 요청의 `Content-Type`이 `image/png`인지 확인할 수 있습니다.

## 실제 웹 서버

NGINX와 Apache 같은 실제 웹 서버도 정적 파일 서빙을 수행합니다.

요청 path를 파일 path로 매핑하고, 파일을 읽고, MIME Type과 Content-Length를 설정해서 응답하는 큰 흐름은 같습니다.
