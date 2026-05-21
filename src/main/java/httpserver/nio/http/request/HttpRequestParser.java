package httpserver.nio.http.request;

import java.util.Map;
import java.util.TreeMap;

public class HttpRequestParser {

    private static final String CRLF = "\r\n";
    private static final String HEADER_END = "\r\n\r\n";

    public HttpRequest parse(String rawRequest) {
        if (rawRequest == null || rawRequest.isBlank()) {
            throw new IllegalArgumentException("HTTP request is empty");
        }

        /*
         * HTTP 요청은 Header 영역과 Body 영역 사이에 빈 줄을 둡니다.
         * 그 빈 줄이 CRLF CRLF, 즉 \r\n\r\n 입니다.
         */
        int headerEndIndex = rawRequest.indexOf(HEADER_END);
        String headPart;
        String body;

        if (headerEndIndex >= 0) {
            headPart = rawRequest.substring(0, headerEndIndex);
            body = rawRequest.substring(headerEndIndex + HEADER_END.length());
        } else {
            /*
             * 정상적인 HTTP 요청이라면 \r\n\r\n이 있어야 합니다.
             * 다만 학습용 서버가 바로 죽지 않도록, 현재까지 읽힌 전체 문자열을 header로 취급합니다.
             */
            System.err.println("[parser] Header end marker \\r\\n\\r\\n was not found");
            headPart = rawRequest;
            body = "";
        }

        String[] lines = headPart.split(CRLF);

        if (lines.length == 0 || lines[0].isBlank()) {
            throw new IllegalArgumentException("Request Line is missing");
        }

        /*
         * Request Line 예:
         *
         * GET /hello HTTP/1.1
         *
         * 정확히 method, path, version 세 부분으로 나뉘어야 합니다.
         */
        String requestLine = lines[0];
        String[] requestLineParts = requestLine.split(" ");

        if (requestLineParts.length != 3) {
            throw new IllegalArgumentException("Invalid Request Line: " + requestLine);
        }

        String method = requestLineParts[0];
        String path = requestLineParts[1];
        String version = requestLineParts[2];

        validateRequestLine(method, path, version);

        /*
         * HTTP Header 이름은 대소문자를 구분하지 않습니다.
         * 예를 들어 Host, host, HOST는 같은 header 이름으로 취급해야 합니다.
         *
         * TreeMap에 String.CASE_INSENSITIVE_ORDER를 주면
         * header 이름 조회를 대소문자와 무관하게 할 수 있습니다.
         */
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];

            if (line.isBlank()) {
                continue;
            }

            int colonIndex = line.indexOf(':');

            if (colonIndex <= 0) {
                System.err.println("[parser] Ignoring malformed header: " + line);
                continue;
            }

            String headerName = line.substring(0, colonIndex).trim();
            String headerValue = line.substring(colonIndex + 1).trim();

            headers.put(headerName, headerValue);
        }

        validateRequiredHeaders(version, headers);

        return new HttpRequest(method, path, version, headers, body);
    }

    private void validateRequestLine(String method, String path, String version) {
        /*
         * Step 14: Request Line을 최소한의 HTTP 요청 형식으로 검증합니다.
         *
         * 여기서는 아직 Host header, Content-Length body, chunked body 같은
         * 다음 단계의 검증은 하지 않습니다.
         */
        if (!isValidMethodToken(method)) {
            throw new IllegalArgumentException("Invalid HTTP method: " + method);
        }

        if (!isValidPath(path)) {
            throw new IllegalArgumentException("Invalid HTTP path: " + path);
        }

        if (!isSupportedHttpVersion(version)) {
            throw new IllegalArgumentException("Invalid HTTP version: " + version);
        }
    }

    private boolean isValidMethodToken(String method) {
        /*
         * HTTP method는 공백 없는 token입니다.
         * 이 학습 단계에서는 GET, POST, HEAD처럼 대문자 알파벳으로 된 method만 허용합니다.
         *
         * Router가 실제로 처리하지 않는 method는 이후 405 Method Not Allowed로 응답합니다.
         */
        return method != null && method.matches("[A-Z]+");
    }

    private boolean isValidPath(String path) {
        /*
         * origin-form 요청 target의 가장 단순한 형태는 / 로 시작합니다.
         *
         * 예:
         * GET / HTTP/1.1
         * GET /hello HTTP/1.1
         */
        return path != null && path.startsWith("/") && !path.contains(" ");
    }

    private boolean isSupportedHttpVersion(String version) {
        /*
         * 현재 서버는 HTTP/1.x 학습용 서버입니다.
         * HTTP/1.0과 HTTP/1.1만 유효한 version으로 다룹니다.
         */
        return "HTTP/1.0".equals(version) || "HTTP/1.1".equals(version);
    }

    private void validateRequiredHeaders(String version, Map<String, String> headers) {
        /*
         * Step 15: HTTP/1.1 요청에서는 Host header가 필수입니다.
         *
         * 같은 IP와 port에 여러 도메인을 연결할 수 있기 때문에,
         * 서버는 Host header를 보고 클라이언트가 어떤 host를 요청했는지 알 수 있습니다.
         *
         * 이 단계에서는 Host의 값이 실제 서버 주소와 일치하는지까지는 검사하지 않고,
         * HTTP/1.1 요청에 Host가 존재하고 비어 있지 않은지만 확인합니다.
         */
        if (!"HTTP/1.1".equals(version)) {
            return;
        }

        String host = headers.get("Host");

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("HTTP/1.1 request requires Host header");
        }
    }
}
