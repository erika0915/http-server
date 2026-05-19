package httpserver.nio.http.router;

import httpserver.nio.http.request.HttpRequest;
import httpserver.nio.http.response.HttpResponse;
import httpserver.nio.http.staticfile.StaticFileHandler;

public class Router {

    private final StaticFileHandler staticFileHandler = new StaticFileHandler();

    public HttpResponse handle(HttpRequest request) {
        /*
         * 아직 GET 외의 method는 처리하지 않습니다.
         * POST body 처리도 다음 단계의 관심사입니다.
         */
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return HttpResponse.methodNotAllowed();
        }

        return staticFileHandler.handle(request.getPath());
    }
}
