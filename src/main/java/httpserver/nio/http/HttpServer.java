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
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class HttpServer {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;
    private static final int BUFFER_SIZE = 32 * 1024;

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

            System.out.println("[server] NIO HTTP Server with Keep-Alive started");
            System.out.println("[server] Listening on http://" + HOST + ":" + PORT);
            System.out.println("[server] Try: curl -v localhost:8080/");

            while (true) {
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
         * SelectionKey attachment를 사용해 SocketChannel별 상태를 저장합니다.
         * Keep-Alive에서는 같은 연결에서 여러 요청이 들어올 수 있으므로
         * 연결 단위 로그를 보려면 이런 작은 context가 있으면 편합니다.
         */
        ConnectionContext context = new ConnectionContext(CONNECTION_ID_SEQUENCE.getAndIncrement());
        clientChannel.register(selector, SelectionKey.OP_READ, context);

        System.out.println(context.label() + " connected " + clientChannel.getRemoteAddress());
    }

    private static void readParseAndRespond(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ConnectionContext context = (ConnectionContext) key.attachment();
        ByteBuffer requestBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        try {
            int totalBytesRead = readUntilHeaderEnd(clientChannel, requestBuffer);

            if (totalBytesRead == -1) {
                closeClient(key, clientChannel, context);
                return;
            }

            if (totalBytesRead == 0) {
                return;
            }

            /*
             * read()는 ByteBuffer에 데이터를 쓰는 작업입니다.
             * 문자열로 읽기 위해 flip()으로 읽기 모드로 전환합니다.
             */
            requestBuffer.flip();

            String rawRequest = StandardCharsets.UTF_8
                    .decode(requestBuffer)
                    .toString();

            try {
                HttpRequest request = REQUEST_PARSER.parse(rawRequest);
                int requestNumber = context.nextRequestNumber();
                System.out.println(context.label() + " request #" + requestNumber
                        + " " + request.getMethod() + " " + request.getPath());

                printParsedRequest(context, clientChannel, totalBytesRead, rawRequest, request);
                HttpResponse response = ROUTER.handle(request);
                boolean keepAlive = shouldKeepAlive(request);

                /*
                 * HTTP/1.1은 기본적으로 keep-alive입니다.
                 * 단, 클라이언트가 Connection: close를 보내면 응답에도 close를 넣고 연결을 닫습니다.
                 */
                response.setConnection(keepAlive);

                printSelectedRoute(context, request, response, keepAlive);
                writeResponse(clientChannel, response);

                if (keepAlive) {
                    System.out.println(context.label() + " keep-alive enabled");
                    return;
                }
            } catch (IllegalArgumentException e) {
                System.err.println(context.label() + " failed to parse request: " + e.getMessage());
                printRawRequest(rawRequest);
                HttpResponse response = HttpResponse.notFound();
                response.setConnection(false);
                writeResponse(clientChannel, response);
            }

            closeClient(key, clientChannel, context);
        } catch (IOException e) {
            System.err.println(context.label() + " client error: " + e.getMessage());
            closeClient(key, clientChannel, context);
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

    private static int readUntilHeaderEnd(SocketChannel clientChannel, ByteBuffer requestBuffer) throws IOException {
        int totalBytesRead = 0;

        /*
         * 아직 Content-Length 기반 body 읽기를 완성하지 않습니다.
         * 이번 단계에서는 Header 종료 지점인 \r\n\r\n까지만 확실히 감지합니다.
         *
         * 만약 같은 read 안에 body 일부가 같이 들어오면 rawRequest의 body 문자열에 저장될 수는 있습니다.
         */
        while (requestBuffer.hasRemaining()) {
            int bytesRead = clientChannel.read(requestBuffer);

            if (bytesRead == -1) {
                return -1;
            }

            if (bytesRead == 0) {
                break;
            }

            totalBytesRead += bytesRead;

            if (containsHeaderEnd(requestBuffer)) {
                break;
            }
        }

        return totalBytesRead;
    }

    private static boolean containsHeaderEnd(ByteBuffer buffer) {
        int end = buffer.position();

        for (int i = 0; i <= end - 4; i++) {
            if (buffer.get(i) == '\r'
                    && buffer.get(i + 1) == '\n'
                    && buffer.get(i + 2) == '\r'
                    && buffer.get(i + 3) == '\n') {
                return true;
            }
        }

        return false;
    }

    private static void printParsedRequest(
            ConnectionContext context,
            SocketChannel clientChannel,
            int totalBytesRead,
            String rawRequest,
            HttpRequest request
    ) throws IOException {
        System.out.println();
        System.out.println("==================================================");
        System.out.println(context.label() + " parsed HTTP request from " + clientChannel.getRemoteAddress());
        System.out.println(context.label() + " bytes read: " + totalBytesRead);

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
            ConnectionContext context,
            HttpRequest request,
            HttpResponse response,
            boolean keepAlive
    ) {
        System.out.println();
        System.out.println("----- selected route -----");
        System.out.println(context.label() + " request = " + request.getMethod() + " " + request.getPath());
        System.out.println(context.label() + " response = " + response.getStatusCode() + " " + response.getReasonPhrase());
        System.out.println(context.label() + " connection = " + (keepAlive ? "keep-alive" : "close"));
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

    private static void writeResponse(SocketChannel clientChannel, HttpResponse response) throws IOException {
        ByteBuffer responseBuffer = ByteBuffer.wrap(response.toBytes());

        while (responseBuffer.hasRemaining()) {
            clientChannel.write(responseBuffer);
        }
    }

    private static void closeClient(SelectionKey key, SocketChannel clientChannel, ConnectionContext context) {
        try {
            System.out.println(context.label() + " closed " + clientChannel.getRemoteAddress());
            key.cancel();
            clientChannel.close();
        } catch (IOException e) {
            System.err.println(context.label() + " error while closing client: " + e.getMessage());
        }
    }
}
