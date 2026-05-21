package httpserver.nio.http.router;

import httpserver.nio.http.request.HttpRequest;
import httpserver.nio.http.response.HttpResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouterTest {

    private final Router router = new Router();

    @Test
    void postRequestReturnsMethodNotAllowed() {
        HttpRequest request = new HttpRequest(
                "POST", "/", "HTTP/1.1",
                Map.of("Host", "localhost:8080"),
                ""
        );

        HttpResponse response = router.handle(request);

        assertEquals(405, response.getStatusCode());
    }

    @Test
    void deleteRequestReturnsMethodNotAllowed() {
        HttpRequest request = new HttpRequest(
                "DELETE", "/", "HTTP/1.1",
                Map.of("Host", "localhost:8080"),
                ""
        );

        HttpResponse response = router.handle(request);

        assertEquals(405, response.getStatusCode());
    }

    @Test
    void patchRequestReturnsMethodNotAllowed() {
        HttpRequest request = new HttpRequest(
                "PATCH", "/hello", "HTTP/1.1",
                Map.of("Host", "localhost:8080"),
                ""
        );

        HttpResponse response = router.handle(request);

        assertEquals(405, response.getStatusCode());
    }

    @Test
    void getMetricsReturns200WithPrometheusFormat() {
        HttpRequest request = new HttpRequest(
                "GET", "/metrics", "HTTP/1.1",
                Map.of("Host", "localhost:8080"),
                ""
        );

        HttpResponse response = router.handle(request);

        assertEquals(200, response.getStatusCode());
        assertEquals("text/plain; version=0.0.4", response.getHeaders().get("Content-Type"));

        String body = new String(response.getBody());
        assertTrue(body.contains("nio_http_total_requests"));
    }
}
