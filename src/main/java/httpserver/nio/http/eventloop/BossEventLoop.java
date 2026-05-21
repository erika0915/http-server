package httpserver.nio.http.eventloop;

import httpserver.nio.http.logging.ServerLogger;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class BossEventLoop {

    private final ServerSocketChannel serverChannel;
    private final EventLoopGroup workerGroup;
    private final Selector selector;

    public BossEventLoop(ServerSocketChannel serverChannel, EventLoopGroup workerGroup) throws IOException {
        this.serverChannel = serverChannel;
        this.workerGroup = workerGroup;
        this.selector = Selector.open();

        /*
         * Boss EventLoop는 ServerSocketChannel만 감시합니다.
         * 관심 이벤트는 새 연결 수락을 의미하는 OP_ACCEPT 하나뿐입니다.
         */
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public void run() throws IOException {
        Thread.currentThread().setName("boss-thread");
        ServerLogger.info("[boss] started");

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
                    acceptClient();
                }
            }
        }
    }

    private void acceptClient() throws IOException {
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel == null) {
            return;
        }

        clientChannel.configureBlocking(false);

        int connectionId = workerGroup.nextConnectionId();
        WorkerEventLoop worker = workerGroup.nextWorker();

        ServerLogger.debug("[boss] accepted connection conn-" + connectionId);
        ServerLogger.debug("[boss] assigned conn-" + connectionId + " -> " + worker.name());

        worker.register(clientChannel, connectionId);
    }
}
