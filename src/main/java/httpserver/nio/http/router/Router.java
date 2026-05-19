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
         * 아직 GET 외의 method는 처리하지 않습니다.
         * POST body 처리도 다음 단계의 관심사입니다.
         */
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return HttpResponse.methodNotAllowed();
        }

        if ("/metrics".equals(request.getPath())) {
            return metricsHandler.handle();
        }

        return staticFileHandler.handle(request.getPath());
    }
}
