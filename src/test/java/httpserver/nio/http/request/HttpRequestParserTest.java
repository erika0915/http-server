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

    @Test
    void http10RequestWithoutHostPasses() {
        HttpRequest request = parser.parse("GET / HTTP/1.0\r\n\r\n");

        assertEquals("HTTP/1.0", request.getVersion());
        assertEquals("GET", request.getMethod());
    }

    @Test
    void parsesZeroContentLength() {
        HttpRequest request = parser.parse("""
                POST /hello HTTP/1.1\r
                Host: localhost:8080\r
                Content-Length: 0\r
                \r
                """);

        assertEquals("", request.getBody());
    }

    @Test
    void rejectsNegativeContentLength() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                POST /hello HTTP/1.1\r
                Host: localhost:8080\r
                Content-Length: -1\r
                \r
                hello"""));
    }

    @Test
    void rejectsBodyShorterThanContentLength() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                POST /hello HTTP/1.1\r
                Host: localhost:8080\r
                Content-Length: 100\r
                \r
                short"""));
    }

    @Test
    void headerNamesAreCaseInsensitive() {
        HttpRequest request = parser.parse("GET / HTTP/1.1\r\nHOST: localhost:8080\r\n\r\n");

        assertEquals("localhost:8080", request.getHeaders().get("host"));
        assertEquals("localhost:8080", request.getHeaders().get("Host"));
        assertEquals("localhost:8080", request.getHeaders().get("HOST"));
    }

    @Test
    void parsesPathWithQueryString() {
        HttpRequest request = parser.parse("""
                GET /hello?name=test&page=1 HTTP/1.1\r
                Host: localhost:8080\r
                \r
                """);

        assertEquals("/hello?name=test&page=1", request.getPath());
    }

    @Test
    void rejectsLowercaseMethod() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                get / HTTP/1.1\r
                Host: localhost:8080\r
                \r
                """));
    }

    @Test
    void rejectsUnsupportedHttpVersion() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                GET / HTTP/2.0\r
                Host: localhost:8080\r
                \r
                """));
    }

    @Test
    void parsesMultipleChunks() {
        HttpRequest request = parser.parse(
                "POST / HTTP/1.1\r\n" +
                "Host: localhost:8080\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                "3\r\n" +
                "foo\r\n" +
                "3\r\n" +
                "bar\r\n" +
                "0\r\n" +
                "\r\n"
        );

        assertEquals("foobar", request.getBody());
    }
}
