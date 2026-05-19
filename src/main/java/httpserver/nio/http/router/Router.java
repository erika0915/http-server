package httpserver.nio.http.router;

import httpserver.nio.http.request.HttpRequest;
import httpserver.nio.http.response.HttpResponse;

public class Router {

    public HttpResponse handle(HttpRequest request) {
        /*
         * 아직 GET 외의 method는 처리하지 않습니다.
         * POST body 처리도 다음 단계의 관심사입니다.
         */
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return HttpResponse.methodNotAllowed();
        }

        /*
         * 지금은 route table이나 handler 인터페이스를 만들지 않고
         * switch로 path만 단순 분기합니다.
         */
        return switch (request.getPath()) {
            case "/" -> HttpResponse.okText("Hello NIO HTTP Server");
            case "/hello" -> HttpResponse.okText("Hello Router");
            case "/users" -> HttpResponse.okJson("[{\"id\":1,\"name\":\"soohee\"},{\"id\":2,\"name\":\"nio\"}]");
            default -> HttpResponse.notFound();
        };
    }
}
