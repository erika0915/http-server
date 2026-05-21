package httpserver.nio.http.request;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpRequestParserTest {

    private final HttpRequestParser parser = new HttpRequestParser();

    @Test
    void parsesValidGetRequest() {
        HttpRequest request = parser.parse("""
                GET /hello HTTP/1.1\r
                Host: localhost:8080\r
                User-Agent: test\r
                \r
                """);

        assertEquals("GET", request.getMethod());
        assertEquals("/hello", request.getPath());
        assertEquals("HTTP/1.1", request.getVersion());
        assertEquals("localhost:8080", request.getHeaders().get("host"));
        assertEquals("", request.getBody());
    }

    @Test
    void rejectsInvalidRequestLine() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                HELLO THIS IS NOT HTTP\r
                Host: localhost:8080\r
                \r
                """));
    }

    @Test
    void rejectsHttp11RequestWithoutHostHeader() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                GET / HTTP/1.1\r
                \r
                """));
    }

    @Test
    void parsesContentLengthBody() {
        HttpRequest request = parser.parse("""
                POST /hello HTTP/1.1\r
                Host: localhost:8080\r
                Content-Length: 11\r
                \r
                hello world""");

        assertEquals("POST", request.getMethod());
        assertEquals("hello world", request.getBody());
    }

    @Test
    void rejectsInvalidContentLength() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                POST /hello HTTP/1.1\r
                Host: localhost:8080\r
                Content-Length: abc\r
                \r
                hello"""));
    }

    @Test
    void parsesChunkedBody() {
        HttpRequest request = parser.parse("""
                POST /hello HTTP/1.1\r
                Host: localhost:8080\r
                Transfer-Encoding: chunked\r
                \r
                5\r
                hello\r
                6\r
                 world\r
                0\r
                \r
                """);

        assertEquals("hello world", request.getBody());
    }

    @Test
    void parsesHeadMethod() {
        HttpRequest request = parser.parse("""
                HEAD / HTTP/1.1\r
                Host: localhost:8080\r
                \r
                """);

        assertEquals("HEAD", request.getMethod());
        assertEquals("/", request.getPath());
    }
}
