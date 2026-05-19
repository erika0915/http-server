package httpserver.nio.http.staticfile;

public class MimeTypes {

    private MimeTypes() {
    }

    public static String fromPath(String path) {
        String lowerPath = path.toLowerCase();

        if (lowerPath.endsWith(".html")) {
            return "text/html";
        }

        if (lowerPath.endsWith(".css")) {
            return "text/css";
        }

        if (lowerPath.endsWith(".js")) {
            return "application/javascript";
        }

        if (lowerPath.endsWith(".json")) {
            return "application/json";
        }

        if (lowerPath.endsWith(".png")) {
            return "image/png";
        }

        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) {
            return "image/jpeg";
        }

        return "application/octet-stream";
    }
}
