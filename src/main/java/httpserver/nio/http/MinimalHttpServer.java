package httpserver.nio.http;

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

public class MinimalHttpServer {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;
    private static final int BUFFER_SIZE = 4096;

    private static final String RESPONSE_BODY = "Hello NIO";

    /*
     * HTTP에서는 각 헤더 줄의 끝에 LF(\n)만 쓰지 않고 CRLF(\r\n)를 사용합니다.
     * 그리고 헤더와 본문 사이에는 반드시 빈 줄, 즉 CRLF CRLF가 들어갑니다.
     *
     * Content-Length는 문자열 길이가 아니라 body를 바이트로 바꿨을 때의 길이입니다.
     * "Hello NIO"는 UTF-8에서도 9바이트입니다.
     */
    private static final byte[] RESPONSE_BYTES = buildHttpResponse();

    public static void main(String[] args) {
        /*
         * Selector는 여러 채널의 준비 상태를 한 스레드에서 감시합니다.
         * 이 서버에서는 OP_ACCEPT와 OP_READ만 사용합니다.
         */
        try (Selector selector = Selector.open();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            /*
             * localhost:8080에 서버 채널을 바인딩합니다.
             * 브라우저에서는 http://localhost:8080 으로 접속할 수 있습니다.
             */
            serverChannel.bind(new InetSocketAddress(HOST, PORT));

            /*
             * NIO Selector에 등록하려면 채널이 Non-Blocking 모드여야 합니다.
             */
            serverChannel.configureBlocking(false);

            /*
             * ServerSocketChannel은 새 클라이언트 연결을 받는 역할이므로
             * OP_ACCEPT 이벤트에 관심을 둡니다.
             */
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("[server] Minimal NIO HTTP Server started");
            System.out.println("[server] Listening on http://" + HOST + ":" + PORT);
            System.out.println("[server] Try: curl localhost:8080");

            while (true) {
                /*
                 * 등록된 채널 중 하나라도 준비될 때까지 기다립니다.
                 */
                selector.select();

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();

                    /*
                     * selectedKeys는 자동으로 비워지지 않습니다.
                     * 처리한 key를 제거하지 않으면 같은 이벤트를 반복 처리할 수 있습니다.
                     */
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        acceptClient(selector, key);
                    } else if (key.isReadable()) {
                        readRequestAndWriteResponse(key);
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
         * 아직 HTTP 요청을 파싱하지 않습니다.
         * 클라이언트가 보낸 바이트를 읽기 위해 OP_READ만 등록합니다.
         */
        clientChannel.register(selector, SelectionKey.OP_READ);

        System.out.println("[server] Client connected: " + clientChannel.getRemoteAddress());
    }

    private static void readRequestAndWriteResponse(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer requestBuffer = ByteBuffer.allocate(BUFFER_SIZE);

        try {
            /*
             * 클라이언트가 보낸 HTTP 요청 바이트를 읽습니다.
             * 아직 Request Parser가 없으므로 메서드, path, header를 분석하지 않습니다.
             */
            int bytesRead = clientChannel.read(requestBuffer);

            if (bytesRead == -1) {
                closeClient(key, clientChannel);
                return;
            }

            if (bytesRead == 0) {
                return;
            }

            /*
             * read() 후에는 buffer가 쓰기 모드 상태입니다.
             * 읽은 요청을 문자열로 확인하기 위해 flip()으로 읽기 모드로 바꿉니다.
             */
            requestBuffer.flip();

            String requestText = StandardCharsets.UTF_8
                    .decode(requestBuffer)
                    .toString();

            printRequest(clientChannel, requestText);

            /*
             * Echo Server와 다르게, 요청 내용을 그대로 돌려주지 않습니다.
             * 브라우저와 curl이 이해할 수 있는 HTTP Response 형식으로 응답합니다.
             */
            ByteBuffer responseBuffer = ByteBuffer.wrap(RESPONSE_BYTES);

            /*
             * 응답은 아주 작기 때문에 현재 이벤트 안에서 모두 write합니다.
             * 실전 서버에서는 write가 한 번에 끝나지 않을 수 있어 OP_WRITE 처리가 필요할 수 있습니다.
             */
            while (responseBuffer.hasRemaining()) {
                clientChannel.write(responseBuffer);
            }

            /*
             * 응답 헤더에 Connection: close를 보냈으므로 응답을 보낸 뒤 연결을 닫습니다.
             * Keep-Alive는 아직 구현하지 않습니다.
             */
            closeClient(key, clientChannel);
        } catch (IOException e) {
            System.err.println("[server] Client error: " + e.getMessage());
            closeClient(key, clientChannel);
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

    private static void printRequest(SocketChannel clientChannel, String requestText) throws IOException {
        /*
         * 콘솔에서 CRLF를 눈으로 볼 수 있게 \r 문자를 표시합니다.
         * \n은 실제 줄바꿈으로 남겨 브라우저 요청 구조를 읽기 쉽게 둡니다.
         */
        String visibleRequest = requestText.replace("\r", "\\r");

        System.out.println();
        System.out.println("[server] HTTP request from " + clientChannel.getRemoteAddress());
        System.out.println("----- request start -----");
        System.out.print(visibleRequest);
        if (!visibleRequest.endsWith("\n")) {
            System.out.println();
        }
        System.out.println("----- request end -----");
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
