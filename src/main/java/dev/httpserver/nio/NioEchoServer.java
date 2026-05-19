package dev.httpserver.nio;

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

public class NioEchoServer {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;
    private static final int BUFFER_SIZE = 1024;

    public static void main(String[] args) {
        /*
         * Selector는 여러 Channel을 한 스레드에서 감시하는 이벤트 감시자입니다.
         *
         * Blocking I/O에서는 accept(), read()를 호출한 스레드가 직접 멈춰 기다립니다.
         * NIO에서는 Channel을 Non-Blocking 모드로 만들고 Selector에 등록합니다.
         * 그러면 Selector가 "지금 accept 가능한 채널", "지금 read 가능한 채널"을 알려줍니다.
         */
        try (Selector selector = Selector.open();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            /*
             * ServerSocketChannel은 ServerSocket의 NIO 버전이라고 보면 됩니다.
             * TCP 연결 요청을 받을 수 있는 서버 채널입니다.
             */
            serverChannel.bind(new InetSocketAddress(HOST, PORT));

            /*
             * NIO Echo Server에서 가장 중요한 설정입니다.
             *
             * false로 설정하면 accept(), read(), write() 같은 작업이
             * 준비되지 않았을 때 스레드를 오래 멈춰 세우지 않습니다.
             */
            serverChannel.configureBlocking(false);

            /*
             * 서버 채널을 Selector에 등록합니다.
             *
             * OP_ACCEPT는 "새로운 클라이언트 연결을 accept할 준비가 됨"이라는 이벤트입니다.
             * 서버 채널은 클라이언트 연결을 받는 역할이므로 OP_ACCEPT에 관심을 둡니다.
             */
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("[server] NIO Echo Server started");
            System.out.println("[server] Listening on " + HOST + ":" + PORT);
            System.out.println("[server] Test with: nc localhost 8080");

            while (true) {
                /*
                 * selector.select()는 등록된 채널 중 하나라도 준비될 때까지 기다립니다.
                 *
                 * Blocking I/O의 accept(), read()처럼 특정 클라이언트 하나를 기다리는 것이 아니라,
                 * Selector에 등록된 여러 채널 중 이벤트가 생긴 채널을 기다립니다.
                 */
                selector.select();

                /*
                 * selectedKeys는 이번 select()에서 "준비됨" 상태가 된 이벤트 목록입니다.
                 * 각 SelectionKey는 어떤 채널에서 어떤 이벤트가 발생했는지를 담고 있습니다.
                 */
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();

                    /*
                     * 처리한 key는 반드시 selectedKeys에서 제거해야 합니다.
                     * 제거하지 않으면 다음 루프에서도 같은 key가 계속 남아 중복 처리될 수 있습니다.
                     */
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        acceptClient(selector, key);
                    } else if (key.isReadable()) {
                        echoToClient(key);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[server] Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void acceptClient(Selector selector, SelectionKey key) throws IOException {
        /*
         * OP_ACCEPT 이벤트는 ServerSocketChannel에서 발생합니다.
         * key.channel()을 ServerSocketChannel로 캐스팅해서 실제 클라이언트 연결을 accept합니다.
         */
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        /*
         * Non-Blocking 모드에서는 accept()가 null을 반환할 수도 있습니다.
         * Selector가 accept 가능하다고 알려준 직후라도, 안전하게 null 체크를 해둡니다.
         */
        if (clientChannel == null) {
            return;
        }

        /*
         * 클라이언트 채널도 반드시 Non-Blocking 모드로 바꿔야 Selector에 등록할 수 있습니다.
         */
        clientChannel.configureBlocking(false);

        /*
         * 클라이언트 채널은 데이터를 읽는 대상이므로 OP_READ에 관심을 둡니다.
         *
         * attach()처럼 별도 객체를 붙일 수도 있지만,
         * 학습용 Echo Server에서는 단순하게 이벤트가 올 때마다 ByteBuffer를 새로 만듭니다.
         */
        clientChannel.register(selector, SelectionKey.OP_READ);

        System.out.println("[server] Client connected: " + clientChannel.getRemoteAddress());
    }

    private static void echoToClient(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);

        try {
            /*
             * channel.read(buffer)는 클라이언트가 보낸 바이트를 ByteBuffer에 씁니다.
             *
             * 반환값 의미:
             * - 양수: 읽은 바이트 수
             * - 0: 현재 읽을 데이터가 없음
             * - -1: 클라이언트가 연결을 정상 종료함
             */
            int bytesRead = clientChannel.read(buffer);

            if (bytesRead == -1) {
                closeClient(key, clientChannel);
                return;
            }

            if (bytesRead == 0) {
                return;
            }

            /*
             * read(buffer) 이후 buffer는 write mode 상태입니다.
             * 즉, position은 방금 쓴 데이터 끝을 가리키고 limit은 capacity를 가리킵니다.
             *
             * channel.write(buffer)로 다시 보내려면 buffer를 read mode로 바꿔야 합니다.
             * flip()은 limit을 현재 position으로 바꾸고 position을 0으로 되돌립니다.
             */
            buffer.flip();

            /*
             * echo 자체는 바이트를 그대로 돌려주는 것이 핵심입니다.
             * 다만 학습/디버깅을 위해 같은 buffer를 복사해서 UTF-8 문자열로 로그를 남깁니다.
             *
             * asReadOnlyBuffer()는 원본 buffer의 position을 움직이지 않는 별도 view를 만듭니다.
             * 그래서 아래 로그를 찍어도 이어지는 write(buffer)에 영향을 주지 않습니다.
             */
            String receivedText = StandardCharsets.UTF_8
                    .decode(buffer.asReadOnlyBuffer())
                    .toString()
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");

            System.out.println("[server] Received "
                    + bytesRead
                    + " bytes from "
                    + clientChannel.getRemoteAddress()
                    + " -> "
                    + receivedText);

            /*
             * Echo Server이므로 읽은 바이트를 그대로 클라이언트에게 다시 씁니다.
             *
             * Non-Blocking write는 한 번에 모든 바이트를 쓰지 못할 수도 있습니다.
             * 이 예제에서는 학습을 위해 간단히 buffer에 남은 바이트를 현재 이벤트 안에서 모두 쓰려고 시도합니다.
             */
            while (buffer.hasRemaining()) {
                clientChannel.write(buffer);
            }

            /*
             * clear()는 buffer를 다시 write mode로 돌립니다.
             * 여기서는 메서드가 끝나며 buffer를 버리므로 필수는 아니지만,
             * ByteBuffer의 기본 흐름을 보여주기 위해 명시합니다.
             */
            buffer.clear();
        } catch (IOException e) {
            System.err.println("[server] Client error: " + e.getMessage());
            closeClient(key, clientChannel);
        }
    }

    private static void closeClient(SelectionKey key, SocketChannel clientChannel) {
        try {
            System.out.println("[server] Client closed connection: " + clientChannel.getRemoteAddress());
            key.cancel();
            clientChannel.close();
        } catch (IOException e) {
            System.err.println("[server] Error while closing client: " + e.getMessage());
        }
    }
}
