package httpserver.nio.http.staticfile;

import java.util.HashMap;
import java.util.Map;

public class MimeTypes {

    private static final Map<String, String> TYPES = new HashMap<>();

    static {
        TYPES.put(".html", "text/html; charset=utf-8");
        TYPES.put(".htm", "text/html; charset=utf-8");
        TYPES.put(".css", "text/css; charset=utf-8");
        TYPES.put(".js", "application/javascript; charset=utf-8");
        TYPES.put(".mjs", "application/javascript; charset=utf-8");
        TYPES.put(".json", "application/json; charset=utf-8");
        TYPES.put(".txt", "text/plain; charset=utf-8");
        TYPES.put(".csv", "text/csv; charset=utf-8");
        TYPES.put(".xml", "application/xml; charset=utf-8");
        TYPES.put(".svg", "image/svg+xml");
        TYPES.put(".png", "image/png");
        TYPES.put(".jpg", "image/jpeg");
        TYPES.put(".jpeg", "image/jpeg");
        TYPES.put(".gif", "image/gif");
        TYPES.put(".webp", "image/webp");
        TYPES.put(".ico", "image/x-icon");
        TYPES.put(".pdf", "application/pdf");
        TYPES.put(".zip", "application/zip");
        TYPES.put(".wasm", "application/wasm");
        TYPES.put(".woff", "font/woff");
        TYPES.put(".woff2", "font/woff2");
        TYPES.put(".ttf", "font/ttf");
        TYPES.put(".otf", "font/otf");
        TYPES.put(".mp3", "audio/mpeg");
        TYPES.put(".mp4", "video/mp4");
    }

    private MimeTypes() {
    }

    public static String fromPath(String path) {
        String lowerPath = path.toLowerCase();

        for (Map.Entry<String, String> entry : TYPES.entrySet()) {
            if (lowerPath.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        return "application/octet-stream";
    }
}
