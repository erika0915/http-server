package httpserver.nio.http.integration;

import httpserver.nio.http.request.HttpRequest;
import httpserver.nio.http.request.HttpRequestParser;
import httpserver.nio.http.response.HttpResponse;
import httpserver.nio.http.router.Router;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpServerFlowTest {

    private final HttpRequestParser parser = new HttpRequestParser();
    private final Router router = new Router();

    @Test
    void handlesGetStaticFileFlow() {
        HttpRequest request = parser.parse("""
                GET / HTTP/1.1\r
                Host: localhost:8080\r
                \r
                """);

        HttpResponse response = router.handle(request);

        assertEquals(200, response.getStatusCode());
        assertEquals("text/html; charset=utf-8", response.getHeaders().get("Content-Type"));
        assertTrue(response.getHeaders().containsKey("ETag"));
        assertTrue(response.getHeaders().containsKey("Last-Modified"));
        assertTrue(response.getBody().length > 0);
    }

    @Test
    void handlesHeadStaticFileFlowWithoutBody() {
        HttpRequest request = parser.parse("""
                HEAD / HTTP/1.1\r
                Host: localhost:8080\r
                \r
                """);

        HttpResponse response = router.handle(request);

        assertEquals(200, response.getStatusCode());
        assertEquals("text/html; charset=utf-8", response.getHeaders().get("Content-Type"));
        assertTrue(Integer.parseInt(response.getHeaders().get("Content-Length")) > 0);
        assertEquals(0, response.getBody().length);
    }

    @Test
    void returnsMethodNotAllowedForPostAfterBodyParsing() {
        HttpRequest request = parser.parse("""
                POST /hello HTTP/1.1\r
                Host: localhost:8080\r
                Content-Length: 11\r
                \r
                hello world""");

        HttpResponse response = router.handle(request);

        assertEquals(405, response.getStatusCode());
        assertEquals("hello world", request.getBody());
    }

    @Test
    void returnsNotFoundForUnknownPath() {
        HttpRequest request = parser.parse("""
                GET /unknown.html HTTP/1.1\r
                Host: localhost:8080\r
                \r
                """);

        HttpResponse response = router.handle(request);

        assertEquals(404, response.getStatusCode());
    }
}
