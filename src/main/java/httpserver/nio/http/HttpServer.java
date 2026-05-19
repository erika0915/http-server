package httpserver.nio.http;

import httpserver.nio.http.eventloop.BossEventLoop;
import httpserver.nio.http.eventloop.EventLoopGroup;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;

public class HttpServer {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;

    public static void main(String[] args) {
        int workerCount = resolveWorkerCount(args);

        try (ServerSocketChannel serverChannel = ServerSocketChannel.open()) {
            serverChannel.bind(new InetSocketAddress(HOST, PORT));
            serverChannel.configureBlocking(false);

            /*
             * Multi EventLoop 구조:
             *
             * - BossEventLoop: ServerSocketChannel의 OP_ACCEPT만 처리합니다.
             * - WorkerEventLoop: SocketChannel의 OP_READ / OP_WRITE를 처리합니다.
             *
             * main thread는 boss 역할을 수행하고, worker들은 별도 thread에서 실행됩니다.
             */
            EventLoopGroup workerGroup = new EventLoopGroup(workerCount);
            workerGroup.start();

            BossEventLoop bossEventLoop = new BossEventLoop(serverChannel, workerGroup);

            System.out.println("[server] NIO HTTP Server with Multi EventLoop started");
            System.out.println("[server] Listening on http://" + HOST + ":" + PORT);
            System.out.println("[server] workers=" + workerCount);
            System.out.println("[server] Try: curl -v localhost:8080/");

            bossEventLoop.run();
        } catch (IOException e) {
            System.err.println("[server] Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static int resolveWorkerCount(String[] args) {
        if (args.length > 0) {
            return Math.max(1, Integer.parseInt(args[0]));
        }

        int availableProcessors = Runtime.getRuntime().availableProcessors();
        return Math.max(2, availableProcessors);
    }
}
