package httpserver.nio.http.staticfile;

import httpserver.nio.http.response.HttpResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StaticFileHandler {

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

    public HttpResponse handle(String requestPath) {
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

            if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                return HttpResponse.notFound();
            }

            byte[] fileBytes = Files.readAllBytes(filePath);
            String contentType = MimeTypes.fromPath(filePath.toString());

            System.out.println("[static] content type = " + contentType);
            System.out.println("[static] file bytes   = " + fileBytes.length);

            return HttpResponse.okBytes(contentType, fileBytes);
        } catch (IOException e) {
            System.err.println("[static] Failed to read static file: " + e.getMessage());
            return HttpResponse.notFound();
        }
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
}
