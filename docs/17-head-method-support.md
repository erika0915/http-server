# HEAD Method Support

Step 17은 HTTP `HEAD` method를 지원하는 단계입니다.

## 목표

`HEAD`는 `GET`과 거의 같지만 response body를 전송하지 않습니다.

서버는 같은 리소스에 대해 `GET`과 같은 status/header를 계산하되, 실제 body bytes는 보내지 않아야 합니다.

## 구현 내용

`Router`가 `HEAD` method를 허용하도록 변경했습니다.

동작:

```text
GET  /index.html -> header + body
HEAD /index.html -> header only
```

`HttpResponse`에는 body 없이 응답을 만들 수 있는 `withoutBody()`를 추가했습니다.

중요한 점:

```text
Content-Length는 원래 GET body 길이를 유지한다.
실제로 전송되는 body는 없다.
```

## 핵심 개념

`HEAD`는 클라이언트가 리소스의 metadata만 확인하고 싶을 때 사용할 수 있습니다.

예를 들어 파일이 존재하는지, Content-Type이 무엇인지, Content-Length가 얼마인지 확인할 수 있습니다.

하지만 body를 받지 않으므로 네트워크 비용을 줄일 수 있습니다.

## 코드 흐름

```text
HttpRequestParser.parse(rawRequest)
-> method = HEAD
-> Router.handle(request)
-> GET과 같은 방식으로 HttpResponse 생성
-> response.withoutBody()
-> header만 전송
```

## 실행 방법

서버를 실행합니다.

```bash
java -cp build/classes/java/main httpserver.nio.http.HttpServer
```

## 테스트 방법

```bash
curl -I http://localhost:8080/
```

또는:

```bash
curl -v -X HEAD http://localhost:8080/
```

예상 결과:

```text
HTTP/1.1 200 OK
Content-Type: text/html
Content-Length: ...
```

응답 body는 출력되지 않아야 합니다.

## 완료 기준

```text
HEAD 요청을 405로 거부하지 않는다.
HEAD 요청은 GET과 같은 status/header를 반환한다.
HEAD 응답은 body를 전송하지 않는다.
Content-Length는 원래 body 길이를 유지한다.
```
