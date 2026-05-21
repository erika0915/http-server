package httpserver.nio.http.router;

import httpserver.nio.http.request.HttpRequest;
import httpserver.nio.http.response.HttpResponse;
import httpserver.nio.http.metrics.MetricsHandler;
import httpserver.nio.http.metrics.ServerMetrics;
import httpserver.nio.http.staticfile.StaticFileHandler;

public class Router {

    private final StaticFileHandler staticFileHandler = new StaticFileHandler();
    private final MetricsHandler metricsHandler = new MetricsHandler(ServerMetrics.global());

    public HttpResponse handle(HttpRequest request) {
        /*
         * 현재 서버는 정적 리소스를 읽는 GET과, 같은 header만 확인하는 HEAD를 지원합니다.
         * POST body를 실제 application 로직에 연결하는 일은 아직 하지 않습니다.
         */
        boolean getMethod = "GET".equalsIgnoreCase(request.getMethod());
        boolean headMethod = "HEAD".equalsIgnoreCase(request.getMethod());

        if (!getMethod && !headMethod) {
            return HttpResponse.methodNotAllowed();
        }

        HttpResponse response;

        if ("/metrics".equals(request.getPath())) {
            response = metricsHandler.handle();
        } else {
            response = staticFileHandler.handle(request);
        }

        if (headMethod) {
            return response.withoutBody();
        }

        return response;
    }
}
