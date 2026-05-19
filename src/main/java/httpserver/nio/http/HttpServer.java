package httpserver.nio.http;

import httpserver.nio.http.response.HttpResponse;
import httpserver.nio.http.router.Router;
import httpserver.nio.http.request.HttpRequest;
import httpserver.nio.http.request.HttpRequestParser;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpServer {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;
    private static final long IDLE_TIMEOUT_MILLIS = 30_000;
    private static final long SELECT_TIMEOUT_MILLIS = 1_000;
    private static final int MAX_HEADER_BYTES = 8 * 1024;
    private static final HttpRequestParser REQUEST_PARSER = new HttpRequestParser();
    private static final Router ROUTER = new Router();
    private static final AtomicInteger CONNECTION_ID_SEQUENCE = new AtomicInteger(1);
    private static final Map<Integer, Connection> ACTIVE_CONNECTIONS = new HashMap<>();
    private static int lastPrintedActiveConnectionCount = -1;

    public static void main(String[] args) {
        try (Selector selector = Selector.open();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            /*
             * 브라우저, curl, nc가 접속할 주소입니다.
             */
            serverChannel.bind(new InetSocketAddress(HOST, PORT));

            /*
             * Selector에 등록되는 Channel은 Non-Blocking 모드여야 합니다.
             */
            serverChannel.configureBlocking(false);

            /*
             * 서버 채널은 새 연결 수락에 관심이 있으므로 OP_ACCEPT로 등록합니다.
             */
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("[server] NIO HTTP Server with Timeout/Cleanup started");
            System.out.println("[server] Listening on http://" + HOST + ":" + PORT);
            System.out.println("[server] Try: curl -v localhost:8080/");

            while (true) {
                /*
                 * Event Loop의 핵심입니다.
                 *
                 * Selector는 여러 SocketChannel 중 지금 작업 가능한 채널만 알려줍니다.
                 * 각 SocketChannel에는 SelectionKey attachment로 Connection 객체가 붙어 있습니다.
                 *
                 * 그래서 Event Loop는 channel 자체보다 Connection을 꺼내서
                 * "이 연결이 지금 어떤 상태인가?"를 중심으로 처리할 수 있습니다.
                 */
                /*
                 * select(timeout)을 사용하면 새 이벤트가 없어도 주기적으로 깨어날 수 있습니다.
                 * 이 덕분에 아무 요청도 보내지 않는 idle keep-alive 연결도 정리할 수 있습니다.
                 */
                selector.select(SELECT_TIMEOUT_MILLIS);

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        acceptClient(selector, key);
                        continue;
                    }

                    /*
                     * OP_READ:
                     * 요청 바이트를 Connection.readBuffer에 누적합니다.
                     * 아직 \r\n\r\n이 오지 않았다면 요청이 완성되지 않은 것이므로
                     * Router를 호출하지 않고 계속 읽기 이벤트를 기다립니다.
                     */
                    if (key.isReadable()) {
                        readAndMaybePrepareResponse(key);
                    }

                    /*
                     * OP_WRITE:
                     * Connection.writeBuffer에 남아 있는 응답 바이트를 전송합니다.
                     * 한 번에 다 쓰지 못하면 OP_WRITE를 유지하고,
                     * 다 쓰면 keep-alive 여부에 따라 OP_READ로 돌아가거나 연결을 닫습니다.
                     */
                    if (key.isValid() && key.isWritable()) {
                        writeResponse(key);
                    }
                }

                cleanupConnections(System.currentTimeMillis());
            }
        } catch (IOException e) {
            System.err.println("[server] Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void acceptClient(Selector selector, SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel == null) {
            return;
        }

        clientChannel.configureBlocking(false);

        /*
         * SelectionKey attachment를 사용해 SocketChannel에 Connection 객체를 붙입니다.
         *
         * Selector는 이벤트가 발생한 SelectionKey를 돌려주므로,
         * key.attachment()로 해당 채널의 상태 객체를 바로 꺼낼 수 있습니다.
         */
        Connection connection = new Connection(CONNECTION_ID_SEQUENCE.getAndIncrement(), clientChannel);
        SelectionKey clientKey = clientChannel.register(selector, SelectionKey.OP_READ, connection);
        connection.attachSelectionKey(clientKey);
        ACTIVE_CONNECTIONS.put(connection.connectionId(), connection);
        printActiveConnectionCount();
    }

    private static void readAndMaybePrepareResponse(SelectionKey key) {
        Connection connection = (Connection) key.attachment();

        try {
            int totalBytesRead = connection.appendReadData();

            if (totalBytesRead == -1) {
                closeConnection(key, connection, "client-closed");
                return;
            }

            if (connection.requestBytes() > MAX_HEADER_BYTES) {
                System.err.println(connection.label() + " header too large");
                HttpResponse response = HttpResponse.requestHeaderFieldsTooLarge();
                connection.prepareResponse(response, false);
                printErrorResponse(connection, response);
                key.interestOps(SelectionKey.OP_WRITE);
                return;
            }

            if (!connection.isRequestComplete()) {
                if (!connection.canReadMore()) {
                    System.err.println(connection.label() + " read buffer is full before request was complete");
                    HttpResponse response = HttpResponse.requestHeaderFieldsTooLarge();
                    connection.prepareResponse(response, false);
                    printErrorResponse(connection, response);
                    key.interestOps(SelectionKey.OP_WRITE);
                    return;
                }

                if (totalBytesRead == 0) {
                    return;
                }

                System.out.println(connection.label() + " request incomplete, waiting for more data");
                key.interestOps(SelectionKey.OP_READ);
                return;
            }

            if (totalBytesRead == 0) {
                return;
            }

            System.out.println(connection.label() + " request complete");
            String rawRequest = connection.readRequestText();

            try {
                HttpRequest request = REQUEST_PARSER.parse(rawRequest);
                connection.setCurrentRequest(request);

                int requestNumber = connection.nextRequestNumber();
                System.out.println(connection.label() + " request #" + requestNumber
                        + " " + request.getMethod() + " " + request.getPath());

                printParsedRequest(connection, connection.requestBytes(), rawRequest, request);
                HttpResponse response = ROUTER.handle(request);
                boolean keepAlive = shouldKeepAlive(request);

                /*
                 * HTTP/1.1은 기본적으로 keep-alive입니다.
                 * 단, 클라이언트가 Connection: close를 보내면 응답에도 close를 넣고 연결을 닫습니다.
                 */
                connection.prepareResponse(response, keepAlive);

                printSelectedRoute(connection, request, response, keepAlive);
                key.interestOps(SelectionKey.OP_WRITE);
            } catch (IllegalArgumentException e) {
                System.err.println(connection.label() + " malformed request: " + e.getMessage());
                printRawRequest(rawRequest);
                HttpResponse response = HttpResponse.badRequest();
                connection.prepareResponse(response, false);
                printErrorResponse(connection, response);
                key.interestOps(SelectionKey.OP_WRITE);
            }
        } catch (IOException e) {
            System.err.println(connection.label() + " IOException during read, closing: " + e.getMessage());
            closeConnection(key, connection, "read-error");
        } catch (RuntimeException e) {
            System.err.println(connection.label() + " unexpected read error: " + e.getMessage());
            prepareInternalServerError(key, connection);
        }
    }

    private static void writeResponse(SelectionKey key) {
        Connection connection = (Connection) key.attachment();

        try {
            boolean responseComplete = connection.writePendingResponse();

            if (!responseComplete) {
                key.interestOps(SelectionKey.OP_WRITE);
                return;
            }

            if (connection.keepAlive()) {
                System.out.println(connection.label() + " keep-alive " + connection.keepAlive());
                connection.readyToReadNextRequest();
                key.interestOps(SelectionKey.OP_READ);
                return;
            }

            closeConnection(key, connection, "connection-close");
        } catch (IOException e) {
            System.err.println(connection.label() + " IOException during write, closing: " + e.getMessage());
            closeConnection(key, connection, "write-error");
        }
    }

    private static void prepareInternalServerError(SelectionKey key, Connection connection) {
        if (!key.isValid()) {
            closeConnection(key, connection, "internal-error");
            return;
        }

        HttpResponse response = HttpResponse.internalServerError();
        connection.prepareResponse(response, false);
        printErrorResponse(connection, response);
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private static boolean shouldKeepAlive(HttpRequest request) {
        String connection = request.getHeaders().get("Connection");

        if (connection != null && "close".equalsIgnoreCase(connection.trim())) {
            return false;
        }

        /*
         * 이번 단계의 규칙:
         * - HTTP/1.1 기본은 keep-alive
         * - Connection: close면 종료
         *
         * HTTP/1.0의 keep-alive 확장 규칙은 지금 단계에서 깊게 다루지 않습니다.
         */
        return "HTTP/1.1".equalsIgnoreCase(request.getVersion());
    }

    private static void printParsedRequest(
            Connection connection,
            int totalBytesRead,
            String rawRequest,
            HttpRequest request
    ) throws IOException {
        System.out.println();
        System.out.println("==================================================");
        System.out.println(connection.label() + " parsed HTTP request from " + connection.channel().getRemoteAddress());
        System.out.println(connection.label() + " bytes read: " + totalBytesRead);

        printRawRequest(rawRequest);

        System.out.println();
        System.out.println("----- parsed request -----");
        System.out.println("method  = " + request.getMethod());
        System.out.println("path    = " + request.getPath());
        System.out.println("version = " + request.getVersion());

        System.out.println();
        System.out.println("headers = " + request.getHeaders());
        System.out.println("header count = " + request.getHeaders().size());

        System.out.println();
        System.out.println("body = \"" + request.getBody() + "\"");
        System.out.println("==================================================");
    }

    private static void printSelectedRoute(
            Connection connection,
            HttpRequest request,
            HttpResponse response,
            boolean keepAlive
    ) {
        System.out.println();
        System.out.println("----- selected route -----");
        System.out.println(connection.label() + " request = " + request.getMethod() + " " + request.getPath());
        System.out.println(connection.label() + " response = " + response.getStatusCode() + " " + response.getReasonPhrase());
        System.out.println(connection.label() + " connection = " + (keepAlive ? "keep-alive" : "close"));
        System.out.println("content-type = " + response.getHeaders().get("Content-Type"));
    }

    private static void printErrorResponse(Connection connection, HttpResponse response) {
        System.out.println(connection.label() + " response "
                + response.getStatusCode() + " " + response.getReasonPhrase());
    }

    private static void printRawRequest(String rawRequest) {
        String visibleRawRequest = rawRequest.replace("\r", "\\r");

        System.out.println();
        System.out.println("----- raw request start -----");
        System.out.print(visibleRawRequest);
        if (!visibleRawRequest.endsWith("\n")) {
            System.out.println();
        }
        System.out.println("----- raw request end -----");
    }

    private static void cleanupConnections(long now) {
        Iterator<Connection> iterator = ACTIVE_CONNECTIONS.values().iterator();

        while (iterator.hasNext()) {
            Connection connection = iterator.next();

            if (connection.isClosed()) {
                iterator.remove();
                continue;
            }

            if (connection.isIdleTimeout(now, IDLE_TIMEOUT_MILLIS)) {
                System.out.println(connection.label() + " idle timeout after " + IDLE_TIMEOUT_MILLIS + "ms");

                SelectionKey key = connection.selectionKey();
                if (key != null) {
                    key.cancel();
                }

                connection.close("idle-timeout");
                iterator.remove();
            }
        }

        printActiveConnectionCount();
    }

    private static void closeConnection(SelectionKey key, Connection connection, String reason) {
        key.cancel();
        connection.close(reason);
        ACTIVE_CONNECTIONS.remove(connection.connectionId());
        printActiveConnectionCount();
    }

    private static void printActiveConnectionCount() {
        int activeConnectionCount = ACTIVE_CONNECTIONS.size();

        if (activeConnectionCount != lastPrintedActiveConnectionCount) {
            System.out.println("[server] active connections=" + activeConnectionCount);
            lastPrintedActiveConnectionCount = activeConnectionCount;
        }
    }
}
