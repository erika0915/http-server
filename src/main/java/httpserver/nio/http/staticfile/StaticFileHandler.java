package httpserver.nio.http.staticfile;

import httpserver.nio.http.request.HttpRequest;
import httpserver.nio.http.response.HttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StaticFileHandler {

    private static final DateTimeFormatter HTTP_DATE_FORMATTER =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    private final Path root;

    public StaticFileHandler() {
        /*
         * public 디렉토리를 정적 파일의 root로 사용합니다.
         *
         * toAbsolutePath().normalize()를 해두면 이후 요청 path와 비교할 때
         * 기준 경로가 명확해집니다.
         */
        this.root = Paths.get("public").toAbsolutePath().normalize();
        System.out.println("[static] public root = " + root);
    }

    public HttpResponse handle(HttpRequest request) {
        String requestPath = request.getPath();

        try {
            Path filePath = resolveFilePath(requestPath);

            System.out.println("[static] request path = " + requestPath);
            System.out.println("[static] file path    = " + filePath);

            /*
             * resolve + normalize 후에도 public root 밖으로 나가면 차단합니다.
             * 예: /../../etc/passwd
             */
            if (!filePath.startsWith(root)) {
                System.out.println("[static] blocked path traversal attempt");
                return HttpResponse.notFound();
            }

            if (!Files.exists(filePath)) {
                return HttpResponse.notFound();
            }

            if (Files.isDirectory(filePath)) {
                return handleDirectory(request, filePath);
            }

            if (!Files.isRegularFile(filePath)) {
                return HttpResponse.notFound();
            }

            return handleFile(request, filePath);
        } catch (IOException e) {
            System.err.println("[static] Failed to read static file: " + e.getMessage());
            return HttpResponse.notFound();
        }
    }

    private HttpResponse handleDirectory(HttpRequest request, Path directoryPath) throws IOException {
        Path indexPath = directoryPath.resolve("index.html").normalize();

        if (indexPath.startsWith(root) && Files.isRegularFile(indexPath)) {
            return handleFile(request, indexPath);
        }

        String html = buildDirectoryListingHtml(request.getPath(), directoryPath);
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        HttpResponse response = HttpResponse.okBytes("text/html; charset=utf-8", body);

        response.setHeader("Last-Modified", formatHttpDate(Files.getLastModifiedTime(directoryPath)));
        response.setHeader("ETag", createEtag(directoryPath));

        return response;
    }

    private HttpResponse handleFile(HttpRequest request, Path filePath) throws IOException {
        byte[] fileBytes = Files.readAllBytes(filePath);
        String contentType = MimeTypes.fromPath(filePath.toString());
        FileTime lastModified = Files.getLastModifiedTime(filePath);
        String lastModifiedHeader = formatHttpDate(lastModified);
        String etag = createEtag(filePath);

        System.out.println("[static] content type  = " + contentType);
        System.out.println("[static] file bytes    = " + fileBytes.length);
        System.out.println("[static] last-modified = " + lastModifiedHeader);
        System.out.println("[static] etag          = " + etag);

        if (isNotModified(request.getHeaders(), etag, lastModified)) {
            HttpResponse response = HttpResponse.notModified();
            response.setHeader("ETag", etag);
            response.setHeader("Last-Modified", lastModifiedHeader);
            return response;
        }

        HttpResponse response = HttpResponse.okBytes(contentType, fileBytes);
        response.setHeader("Last-Modified", lastModifiedHeader);
        response.setHeader("ETag", etag);

        return response;
    }

    private boolean isNotModified(Map<String, String> headers, String etag, FileTime lastModified) {
        String ifNoneMatch = headers.get("If-None-Match");

        if (ifNoneMatch != null) {
            return ifNoneMatch.trim().equals(etag);
        }

        String ifModifiedSince = headers.get("If-Modified-Since");

        if (ifModifiedSince == null || ifModifiedSince.isBlank()) {
            return false;
        }

        try {
            Instant requestedTime = DateTimeFormatter.RFC_1123_DATE_TIME
                    .parse(ifModifiedSince, Instant::from);
            long requestedSeconds = requestedTime.getEpochSecond();
            long lastModifiedSeconds = lastModified.toInstant().getEpochSecond();

            return lastModifiedSeconds <= requestedSeconds;
        } catch (RuntimeException e) {
            /*
             * 잘못된 If-Modified-Since 값은 조건부 요청으로 처리하지 않고
             * 일반 200 OK 응답을 반환합니다.
             */
            return false;
        }
    }

    private String buildDirectoryListingHtml(String requestPath, Path directoryPath) throws IOException {
        List<Path> children = new ArrayList<>();

        try (var stream = Files.list(directoryPath)) {
            stream.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(children::add);
        }

        String normalizedRequestPath = requestPath.endsWith("/") ? requestPath : requestPath + "/";
        StringBuilder html = new StringBuilder();

        html.append("<!doctype html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("  <meta charset=\"utf-8\">\n");
        html.append("  <title>Index of ").append(escapeHtml(normalizedRequestPath)).append("</title>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("  <h1>Index of ").append(escapeHtml(normalizedRequestPath)).append("</h1>\n");
        html.append("  <ul>\n");

        if (!directoryPath.equals(root)) {
            html.append("    <li><a href=\"../\">../</a></li>\n");
        }

        for (Path child : children) {
            String name = child.getFileName().toString();
            boolean directory = Files.isDirectory(child);
            String displayName = directory ? name + "/" : name;
            String href = normalizedRequestPath + name + (directory ? "/" : "");

            html.append("    <li><a href=\"")
                    .append(escapeHtml(href))
                    .append("\">")
                    .append(escapeHtml(displayName))
                    .append("</a></li>\n");
        }

        html.append("  </ul>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        return html.toString();
    }

    private Path resolveFilePath(String requestPath) {
        /*
         * / 는 public/index.html로 매핑합니다.
         */
        String relativePath = "/".equals(requestPath) ? "/index.html" : requestPath;

        /*
         * URL path는 / 로 시작합니다.
         * Path.resolve에 그대로 넘기면 OS에 따라 절대 경로처럼 해석될 수 있으므로
         * 앞의 / 를 제거해서 public root 기준 상대 경로로 만듭니다.
         */
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        return root.resolve(relativePath).normalize();
    }

    private String formatHttpDate(FileTime fileTime) {
        return HTTP_DATE_FORMATTER.format(fileTime.toInstant());
    }

    private String createEtag(Path path) throws IOException {
        long size = Files.isDirectory(path) ? 0L : Files.size(path);
        long lastModifiedMillis = Files.getLastModifiedTime(path).toMillis();

        return "\"" + Long.toHexString(size) + "-" + Long.toHexString(lastModifiedMillis) + "\"";
    }

    private String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
