package httpserver.nio.http.response;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class HttpResponse {

    private final int statusCode;
    private final String reasonPhrase;
    private final Map<String, String> headers;
    private final byte[] body;

    private HttpResponse(
            int statusCode,
            String reasonPhrase,
            String contentType,
            byte[] body
    ) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        this.body = Arrays.copyOf(body, body.length);
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
        this.headers.put("Content-Length", String.valueOf(body.length));

        /*
         * 기본값은 close입니다.
         * HttpServer가 요청 Header를 보고 keep-alive 여부를 결정한 뒤 setConnection()으로 덮어씁니다.
         */
        this.headers.put("Connection", "close");
    }

    public static HttpResponse okText(String body) {
        return of(200, "OK", "text/plain", body);
    }

    public static HttpResponse okJson(String body) {
        return of(200, "OK", "application/json", body);
    }

    public static HttpResponse okBytes(String contentType, byte[] body) {
        return new HttpResponse(200, "OK", contentType, body);
    }

    public static HttpResponse notFound() {
        return of(404, "Not Found", "text/plain", "404 Not Found");
    }

    public static HttpResponse badRequest() {
        return of(400, "Bad Request", "text/plain", "400 Bad Request");
    }

    public static HttpResponse requestHeaderFieldsTooLarge() {
        return of(431, "Request Header Fields Too Large", "text/plain", "431 Request Header Fields Too Large");
    }

    public static HttpResponse internalServerError() {
        return of(500, "Internal Server Error", "text/plain", "500 Internal Server Error");
    }

    public static HttpResponse methodNotAllowed() {
        return of(405, "Method Not Allowed", "text/plain", "405 Method Not Allowed");
    }

    private static HttpResponse of(int statusCode, String reasonPhrase, String contentType, String body) {
        return new HttpResponse(
                statusCode,
                reasonPhrase,
                contentType,
                body.getBytes(StandardCharsets.UTF_8)
        );
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

    public void setHeader(String name, String value) {
        headers.put(name, value);
    }

    public void setConnection(boolean keepAlive) {
        setHeader("Connection", keepAlive ? "keep-alive" : "close");
    }

    public byte[] getBody() {
        return Arrays.copyOf(body, body.length);
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

        byte[] headerBytes = response.toString().getBytes(StandardCharsets.UTF_8);
        byte[] responseBytes = new byte[headerBytes.length + body.length];

        System.arraycopy(headerBytes, 0, responseBytes, 0, headerBytes.length);
        System.arraycopy(body, 0, responseBytes, headerBytes.length, body.length);

        return responseBytes;
    }
}
