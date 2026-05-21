package httpserver.nio.http.staticfile;

import httpserver.nio.http.request.HttpRequest;
import httpserver.nio.http.response.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticFileHandlerTest {

    @TempDir
    Path root;

    private StaticFileHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(root.resolve("index.html"), "<!doctype html><h1>Hello</h1>");
        Files.writeString(root.resolve("style.css"), "body { color: black; }");

        handler = new StaticFileHandler(root);
    }

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
        Path directory = root.resolve("test-listing");
        Path file = directory.resolve("example.txt");

        Files.createDirectories(directory);
        Files.writeString(file, "example");

        HttpResponse response = handler.handle(get("/test-listing/"));

        assertEquals(200, response.getStatusCode());

        String body = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(body.contains("<!doctype html>"));
        assertTrue(body.contains("example.txt"));
    }

    @Test
    void returnsNotFoundForMissingFile() {
        HttpResponse response = handler.handle(get("/nonexistent.html"));

        assertEquals(404, response.getStatusCode());
    }

    @Test
    void servesCssFileWithCorrectContentType() throws Exception {
        Files.writeString(root.resolve("main.css"), "body { margin: 0; }");

        HttpResponse response = handler.handle(get("/main.css"));

        assertEquals(200, response.getStatusCode());
        assertEquals("text/css; charset=utf-8", response.getHeaders().get("Content-Type"));
    }

    @Test
    void servesJsFileWithCorrectContentType() throws Exception {
        Files.writeString(root.resolve("app.js"), "console.log('hello');");

        HttpResponse response = handler.handle(get("/app.js"));

        assertEquals(200, response.getStatusCode());
        assertEquals("application/javascript; charset=utf-8", response.getHeaders().get("Content-Type"));
    }

    @Test
    void returnsNotModifiedForFutureIfModifiedSince() throws Exception {
        Files.writeString(root.resolve("page.html"), "<h1>hello</h1>");

        HttpRequest request = new HttpRequest(
                "GET",
                "/page.html",
                "HTTP/1.1",
                Map.of("Host", "localhost:8080", "If-Modified-Since", "Thu, 01 Jan 2099 00:00:00 GMT"),
                ""
        );

        HttpResponse response = handler.handle(request);

        assertEquals(304, response.getStatusCode());
    }

    @Test
    void returnsOctetStreamForUnknownExtension() throws Exception {
        Files.writeString(root.resolve("data.xyz"), "binary data");

        HttpResponse response = handler.handle(get("/data.xyz"));

        assertEquals(200, response.getStatusCode());
        assertEquals("application/octet-stream", response.getHeaders().get("Content-Type"));
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
