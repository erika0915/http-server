package dev.httpserver.nio.http;

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

public class HttpRequestObservationServer {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;

    /*
     * 브라우저는 User-Agent, Accept, Cookie 등 많은 헤더를 보낼 수 있습니다.
     * 학습용 관찰 서버이므로 한 번의 요청을 넉넉히 담기 위해 32KB를 사용합니다.
     */
    private static final int BUFFER_SIZE = 32 * 1024;

    private static final String RESPONSE_BODY = "Hello NIO";
    private static final byte[] RESPONSE_BYTES = buildHttpResponse();

    public static void main(String[] args) {
        try (Selector selector = Selector.open();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            /*
             * localhost:8080에서 브라우저, curl, nc 요청을 받습니다.
             */
            serverChannel.bind(new InetSocketAddress(HOST, PORT));

            /*
             * Selector 기반 서버이므로 ServerSocketChannel을 Non-Blocking 모드로 설정합니다.
             */
            serverChannel.configureBlocking(false);

            /*
             * 서버 채널은 새 TCP 연결을 받는 역할입니다.
             * 그래서 OP_ACCEPT 이벤트에 관심을 둡니다.
             */
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("[server] HTTP Request Observation Server started");
            System.out.println("[server] Listening on http://" + HOST + ":" + PORT);
            System.out.println("[server] Try browser, curl, or nc");

            while (true) {
                /*
                 * 등록된 채널 중 accept/read 가능한 이벤트가 생길 때까지 기다립니다.
                 */
                selector.select();

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();

                    /*
                     * selectedKeys에서 제거하지 않으면 이미 처리한 이벤트를 다시 볼 수 있습니다.
                     */
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        acceptClient(selector, key);
                    } else if (key.isReadable()) {
                        observeRequestAndRespond(key);
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
        clientChannel.register(selector, SelectionKey.OP_READ);

        System.out.println("[server] Client connected: " + clientChannel.getRemoteAddress());
    }

    private static void observeRequestAndRespond(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer requestBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        try {
            /*
             * Non-Blocking 채널에서는 한 번의 read()가 요청 전체를 항상 보장하지 않습니다.
             * 지금은 완전한 Request Parser를 만들지 않는 단계이므로,
             * 현재 도착한 데이터를 최대한 읽고 header 종료 지점(\r\n\r\n)을 관찰합니다.
             */
            int totalBytesRead = 0;

            while (requestBuffer.hasRemaining()) {
                int bytesRead = clientChannel.read(requestBuffer);

                if (bytesRead == -1) {
                    closeClient(key, clientChannel);
                    return;
                }

                if (bytesRead == 0) {
                    break;
                }

                totalBytesRead += bytesRead;

                /*
                 * 지금 단계에서는 header 종료 지점을 찾으면 충분합니다.
                 * body가 있는 요청 처리는 아직 구현하지 않습니다.
                 */
                if (containsHeaderEnd(requestBuffer)) {
                    break;
                }
            }

            if (totalBytesRead == 0) {
                return;
            }

            /*
             * ByteBuffer는 read() 후 쓰기 모드입니다.
             * 문자열로 읽기 위해 flip()으로 읽기 모드로 전환합니다.
             */
            requestBuffer.flip();

            String requestText = StandardCharsets.UTF_8
                    .decode(requestBuffer)
                    .toString();

            printRequestObservation(clientChannel, totalBytesRead, requestText);
            writeHelloNioResponse(clientChannel);
            closeClient(key, clientChannel);
        } catch (IOException e) {
            System.err.println("[server] Client error: " + e.getMessage());
            closeClient(key, clientChannel);
        }
    }

    private static boolean containsHeaderEnd(ByteBuffer buffer) {
        /*
         * buffer는 아직 쓰기 모드입니다.
         * position은 현재까지 읽은 바이트의 끝을 가리킵니다.
         *
         * HTTP header의 끝은 CRLF CRLF, 즉 \r\n\r\n 바이트 패턴입니다.
         * 현재까지 읽은 영역 [0, position) 안에 이 패턴이 있는지 확인합니다.
         */
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

    private static void printRequestObservation(
            SocketChannel clientChannel,
            int totalBytesRead,
            String requestText
    ) throws IOException {
        String visibleRawText = requestText.replace("\r", "\\r");
        boolean headerEndDetected = requestText.contains("\r\n\r\n");
        String headerText = headerEndDetected
                ? requestText.substring(0, requestText.indexOf("\r\n\r\n"))
                : requestText;

        String[] lines = headerText.split("\r\n");
        String requestLine = lines.length > 0 ? lines[0] : "";

        System.out.println();
        System.out.println("==================================================");
        System.out.println("[server] HTTP request from " + clientChannel.getRemoteAddress());
        System.out.println("[server] Bytes read: " + totalBytesRead);
        System.out.println("[server] Header end detected (\\r\\n\\r\\n): " + headerEndDetected);

        /*
         * raw text는 브라우저/curl/nc가 실제로 보낸 문자열을 그대로 관찰하기 위한 로그입니다.
         * 콘솔에서 CR 문자가 보이지 않기 때문에 \r로 치환해서 출력합니다.
         */
        System.out.println();
        System.out.println("----- raw request start -----");
        System.out.print(visibleRawText);
        if (!visibleRawText.endsWith("\n")) {
            System.out.println();
        }
        System.out.println("----- raw request end -----");

        System.out.println();
        System.out.println("----- request line -----");
        System.out.println(requestLine);

        /*
         * Request Line은 보통 다음 세 부분으로 구성됩니다.
         *
         * GET /hello HTTP/1.1
         * - method: GET
         * - path: /hello
         * - version: HTTP/1.1
         */
        String[] requestLineParts = requestLine.split(" ");
        String method = requestLineParts.length > 0 ? requestLineParts[0] : "(missing)";
        String path = requestLineParts.length > 1 ? requestLineParts[1] : "(missing)";
        String version = requestLineParts.length > 2 ? requestLineParts[2] : "(missing)";

        System.out.println("method  = " + method);
        System.out.println("path    = " + path);
        System.out.println("version = " + version);

        System.out.println();
        System.out.println("----- headers -----");

        int headerCount = 0;

        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isEmpty()) {
                continue;
            }

            headerCount++;
            System.out.println(headerCount + ". " + lines[i]);
        }

        System.out.println("[server] Header count: " + headerCount);
        System.out.println("==================================================");
    }

    private static void writeHelloNioResponse(SocketChannel clientChannel) throws IOException {
        ByteBuffer responseBuffer = ByteBuffer.wrap(RESPONSE_BYTES);

        while (responseBuffer.hasRemaining()) {
            clientChannel.write(responseBuffer);
        }
    }

    private static byte[] buildHttpResponse() {
        byte[] bodyBytes = RESPONSE_BODY.getBytes(StandardCharsets.UTF_8);

        String response = ""
                + "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/plain\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n"
                + RESPONSE_BODY;

        return response.getBytes(StandardCharsets.UTF_8);
    }

    private static void closeClient(SelectionKey key, SocketChannel clientChannel) {
        try {
            System.out.println("[server] Closing connection: " + clientChannel.getRemoteAddress());
            key.cancel();
            clientChannel.close();
        } catch (IOException e) {
            System.err.println("[server] Error while closing client: " + e.getMessage());
        }
    }
}
