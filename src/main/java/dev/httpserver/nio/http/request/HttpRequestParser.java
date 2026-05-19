package dev.httpserver.nio.http.request;

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

        return new HttpRequest(method, path, version, headers, body);
    }
}
