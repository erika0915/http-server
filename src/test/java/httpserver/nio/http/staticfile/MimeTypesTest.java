package httpserver.nio.http.staticfile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MimeTypesTest {

    @Test
    void detectsTextMimeTypesWithUtf8Charset() {
        assertEquals("text/html; charset=utf-8", MimeTypes.fromPath("index.html"));
        assertEquals("text/css; charset=utf-8", MimeTypes.fromPath("style.css"));
        assertEquals("application/javascript; charset=utf-8", MimeTypes.fromPath("app.js"));
        assertEquals("application/json; charset=utf-8", MimeTypes.fromPath("data.json"));
        assertEquals("text/plain; charset=utf-8", MimeTypes.fromPath("readme.txt"));
    }

    @Test
    void detectsBinaryAndImageMimeTypes() {
        assertEquals("image/png", MimeTypes.fromPath("image.png"));
        assertEquals("image/jpeg", MimeTypes.fromPath("photo.jpeg"));
        assertEquals("image/svg+xml", MimeTypes.fromPath("icon.svg"));
        assertEquals("image/webp", MimeTypes.fromPath("photo.webp"));
        assertEquals("application/pdf", MimeTypes.fromPath("file.pdf"));
    }

    @Test
    void returnsOctetStreamForUnknownExtension() {
        assertEquals("application/octet-stream", MimeTypes.fromPath("file.unknown"));
    }
}
