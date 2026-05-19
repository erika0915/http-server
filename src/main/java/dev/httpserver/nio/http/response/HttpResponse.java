package dev.httpserver.nio.http.response;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpResponse {

    private final int statusCode;
    private final String reasonPhrase;
    private final Map<String, String> headers;
    private final String body;

    private HttpResponse(
            int statusCode,
            String reasonPhrase,
            String contentType,
            String body
    ) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.body = body;
        this.headers = new LinkedHashMap<>();

        /*
         * Content-Type은 body를 어떤 형식으로 해석해야 하는지 알려줍니다.
         * text/plain이면 일반 텍스트, application/json이면 JSON으로 해석할 수 있습니다.
         */
        this.headers.put("Content-Type", contentType);

        /*
         * Content-Length는 문자 수가 아니라 body를 바이트로 변환했을 때의 길이입니다.
         * 한글이나 이모지처럼 UTF-8에서 여러 바이트가 되는 문자가 있을 수 있기 때문입니다.
         */
        this.headers.put("Content-Length", String.valueOf(body.getBytes(StandardCharsets.UTF_8).length));

        /*
         * 아직 Keep-Alive를 구현하지 않았으므로 모든 응답 후 연결을 닫습니다.
         */
        this.headers.put("Connection", "close");
    }

    public static HttpResponse okText(String body) {
        return new HttpResponse(200, "OK", "text/plain", body);
    }

    public static HttpResponse okJson(String body) {
        return new HttpResponse(200, "OK", "application/json", body);
    }

    public static HttpResponse notFound() {
        return new HttpResponse(404, "Not Found", "text/plain", "404 Not Found");
    }

    public static HttpResponse methodNotAllowed() {
        return new HttpResponse(405, "Method Not Allowed", "text/plain", "405 Method Not Allowed");
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getReasonPhrase() {
        return reasonPhrase;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getBody() {
        return body;
    }

    public byte[] toBytes() {
        StringBuilder response = new StringBuilder();

        /*
         * HTTP 응답의 첫 줄은 status line입니다.
         * 예: HTTP/1.1 200 OK
         */
        response.append("HTTP/1.1 ")
                .append(statusCode)
                .append(' ')
                .append(reasonPhrase)
                .append("\r\n");

        /*
         * HTTP/1.x는 각 header 줄 끝에 CRLF(\r\n)를 사용합니다.
         */
        for (Map.Entry<String, String> header : headers.entrySet()) {
            response.append(header.getKey())
                    .append(": ")
                    .append(header.getValue())
                    .append("\r\n");
        }

        /*
         * header와 body 사이에는 빈 줄이 필요합니다.
         * 이 빈 줄이 \r\n\r\n 구조를 완성합니다.
         */
        response.append("\r\n");
        response.append(body);

        return response.toString().getBytes(StandardCharsets.UTF_8);
    }
}
