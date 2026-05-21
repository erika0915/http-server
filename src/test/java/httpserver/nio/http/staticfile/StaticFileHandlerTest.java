package httpserver.nio.http.staticfile;

import httpserver.nio.http.request.HttpRequest;
import httpserver.nio.http.response.HttpResponse;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticFileHandlerTest {

    private final StaticFileHandler handler = new StaticFileHandler();

    @Test
    void servesIndexHtmlWithMetadataHeaders() {
        HttpResponse response = handler.handle(get("/"));

        assertEquals(200, response.getStatusCode());
        assertEquals("text/html; charset=utf-8", response.getHeaders().get("Content-Type"));
        assertTrue(response.getHeaders().containsKey("Last-Modified"));
        assertTrue(response.getHeaders().containsKey("ETag"));
        assertTrue(response.getBody().length > 0);
    }

    @Test
    void returnsNotModifiedWhenEtagMatches() {
        HttpResponse firstResponse = handler.handle(get("/"));
        String etag = firstResponse.getHeaders().get("ETag");

        HttpResponse secondResponse = handler.handle(new HttpRequest(
                "GET",
                "/",
                "HTTP/1.1",
                Map.of("Host", "localhost:8080", "If-None-Match", etag),
                ""
        ));

        assertEquals(304, secondResponse.getStatusCode());
        assertEquals(0, secondResponse.getBody().length);
    }

    @Test
    void blocksPathTraversal() {
        HttpResponse response = handler.handle(get("/../../etc/passwd"));

        assertEquals(404, response.getStatusCode());
    }

    @Test
    void returnsDirectoryListingWhenIndexIsMissing() throws Exception {
        Path directory = Path.of("public", "test-listing");
        Path file = directory.resolve("example.txt");

        Files.createDirectories(directory);
        Files.writeString(file, "example");

        HttpResponse response = handler.handle(get("/test-listing/"));

        assertEquals(200, response.getStatusCode());

        String body = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(body.contains("<!doctype html>"));
        assertTrue(body.contains("example.txt"));

        Files.deleteIfExists(file);
        Files.deleteIfExists(directory);
    }

    private HttpRequest get(String path) {
        return new HttpRequest(
                "GET",
                path,
                "HTTP/1.1",
                Map.of("Host", "localhost:8080"),
                ""
        );
    }
}
