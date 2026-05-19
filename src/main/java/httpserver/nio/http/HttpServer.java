package httpserver.nio.http;

import httpserver.nio.http.response.HttpResponse;
import httpserver.nio.http.router.Router;
import httpserver.nio.http.request.HttpRequest;
import httpserver.nio.http.request.HttpRequestParser;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpServer {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;
    private static final HttpRequestParser REQUEST_PARSER = new HttpRequestParser();
    private static final Router ROUTER = new Router();
    private static final AtomicInteger CONNECTION_ID_SEQUENCE = new AtomicInteger(1);

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

            System.out.println("[server] NIO HTTP Server with Connection state started");
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
                selector.select();

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
                    } else if (key.isReadable()) {
                        readParseAndRespond(key);
                    }
                }
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
        clientChannel.register(selector, SelectionKey.OP_READ, connection);
    }

    private static void readParseAndRespond(SelectionKey key) {
        Connection connection = (Connection) key.attachment();

        try {
            int totalBytesRead = connection.appendReadData();

            if (totalBytesRead == -1) {
                closeConnection(key, connection);
                return;
            }

            if (totalBytesRead == 0) {
                return;
            }

            String rawRequest = connection.readRequestText();

            try {
                HttpRequest request = REQUEST_PARSER.parse(rawRequest);
                connection.setCurrentRequest(request);

                int requestNumber = connection.nextRequestNumber();
                System.out.println(connection.label() + " request #" + requestNumber
                        + " " + request.getMethod() + " " + request.getPath());

                printParsedRequest(connection, totalBytesRead, rawRequest, request);
                HttpResponse response = ROUTER.handle(request);
                boolean keepAlive = shouldKeepAlive(request);

                /*
                 * HTTP/1.1은 기본적으로 keep-alive입니다.
                 * 단, 클라이언트가 Connection: close를 보내면 응답에도 close를 넣고 연결을 닫습니다.
                 */
                connection.prepareResponse(response, keepAlive);

                printSelectedRoute(connection, request, response, keepAlive);
                connection.writePendingResponse();

                if (keepAlive) {
                    System.out.println(connection.label() + " keep-alive " + connection.keepAlive());
                    connection.readyToReadNextRequest();
                    return;
                }
            } catch (IllegalArgumentException e) {
                System.err.println(connection.label() + " failed to parse request: " + e.getMessage());
                printRawRequest(rawRequest);
                HttpResponse response = HttpResponse.notFound();
                connection.prepareResponse(response, false);
                connection.writePendingResponse();
            }

            closeConnection(key, connection);
        } catch (IOException e) {
            System.err.println(connection.label() + " client error: " + e.getMessage());
            closeConnection(key, connection);
        }
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

    private static void closeConnection(SelectionKey key, Connection connection) {
        key.cancel();
        connection.close();
    }
}
