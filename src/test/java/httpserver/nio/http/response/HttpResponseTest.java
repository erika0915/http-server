package httpserver.nio.http.response;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpResponseTest {

    @Test
    void okTextReturns200WithTextPlainBody() {
        HttpResponse response = HttpResponse.okText("hello");

        assertEquals(200, response.getStatusCode());
        assertEquals("text/plain", response.getHeaders().get("Content-Type"));
        assertEquals("hello", new String(response.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void okJsonReturns200WithApplicationJson() {
        HttpResponse response = HttpResponse.okJson("{\"key\":\"value\"}");

        assertEquals(200, response.getStatusCode());
        assertEquals("application/json", response.getHeaders().get("Content-Type"));
    }

    @Test
    void notFoundReturns404() {
        HttpResponse response = HttpResponse.notFound();

        assertEquals(404, response.getStatusCode());
        assertEquals("Not Found", response.getReasonPhrase());
    }

    @Test
    void badRequestReturns400() {
        HttpResponse response = HttpResponse.badRequest();

        assertEquals(400, response.getStatusCode());
    }

    @Test
    void methodNotAllowedReturns405() {
        HttpResponse response = HttpResponse.methodNotAllowed();

        assertEquals(405, response.getStatusCode());
    }

    @Test
    void internalServerErrorReturns500() {
        HttpResponse response = HttpResponse.internalServerError();

        assertEquals(500, response.getStatusCode());
    }

    @Test
    void requestHeaderFieldsTooLargeReturns431() {
        HttpResponse response = HttpResponse.requestHeaderFieldsTooLarge();

        assertEquals(431, response.getStatusCode());
    }

    @Test
    void notModifiedReturns304WithEmptyBody() {
        HttpResponse response = HttpResponse.notModified();

        assertEquals(304, response.getStatusCode());
        assertEquals(0, response.getBody().length);
    }

    @Test
    void withoutBodyReturnsEmptyBody() {
        HttpResponse original = HttpResponse.okText("hello world");
        HttpResponse head = original.withoutBody();

        assertEquals(0, head.getBody().length);
        assertEquals(200, head.getStatusCode());
    }

    @Test
    void withoutBodyPreservesContentLength() {
        HttpResponse original = HttpResponse.okText("hello world");
        HttpResponse head = original.withoutBody();

        assertEquals("11", head.getHeaders().get("Content-Length"));
    }

    @Test
    void setConnectionToKeepAlive() {
        HttpResponse response = HttpResponse.okText("ok");
        response.setConnection(true);

        assertEquals("keep-alive", response.getHeaders().get("Connection"));
    }

    @Test
    void setConnectionToClose() {
        HttpResponse response = HttpResponse.okText("ok");
        response.setConnection(false);

        assertEquals("close", response.getHeaders().get("Connection"));
    }

    @Test
    void toBytesContainsStatusLineAndHeaderSeparator() {
        HttpResponse response = HttpResponse.okText("test");
        String raw = new String(response.toBytes(), StandardCharsets.UTF_8);

        assertTrue(raw.startsWith("HTTP/1.1 200 OK\r\n"));
        assertTrue(raw.contains("\r\n\r\n"));
    }

    @Test
    void contentLengthMatchesMultibyteBodyByteLength() {
        HttpResponse response = HttpResponse.okText("안녕");

        assertEquals("6", response.getHeaders().get("Content-Length"));
    }
}
