package httpserver.nio.http.eventloop;

import httpserver.nio.http.connection.Connection;
import httpserver.nio.http.request.HttpRequest;
import httpserver.nio.http.request.HttpRequestParser;
import httpserver.nio.http.response.HttpResponse;
import httpserver.nio.http.metrics.ServerMetrics;
import httpserver.nio.http.metrics.WorkerStats;
import httpserver.nio.http.router.Router;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class WorkerEventLoop implements Runnable {

    private static final long IDLE_TIMEOUT_MILLIS = 30_000;
    private static final long SELECT_TIMEOUT_MILLIS = 1_000;
    private static final int MAX_HEADER_BYTES = 8 * 1024;

    private final int workerId;
    private final String name;
    private final Selector selector;
    private final Queue<Registration> registrationQueue;
    private final Map<Integer, Connection> activeConnections;
    private final HttpRequestParser requestParser;
    private final Router router;
    private final ServerMetrics metrics;
    private final WorkerStats stats;

    private int lastPrintedActiveConnectionCount;

    public WorkerEventLoop(int workerId) throws IOException {
        this.workerId = workerId;
        this.name = "worker-" + workerId;
        this.selector = Selector.open();
        this.registrationQueue = new ConcurrentLinkedQueue<>();
        this.activeConnections = new HashMap<>();
        this.requestParser = new HttpRequestParser();
        this.router = new Router();
        this.metrics = ServerMetrics.global();
        this.stats = metrics.workerStats(name);
        this.lastPrintedActiveConnectionCount = -1;
    }

    public String name() {
        return name;
    }

    public void register(SocketChannel channel, int connectionId) {
        /*
         * Boss thread가 Worker의 Selector에 직접 register하면 thread 경합이 생길 수 있습니다.
         * 그래서 queue에 등록 작업을 넣고 selector.wakeup()으로 Worker thread를 깨웁니다.
         *
         * 실제 register는 Worker thread 안의 processRegistrationQueue()에서 수행됩니다.
         */
        registrationQueue.add(new Registration(channel, connectionId));
        selector.wakeup();
    }

    @Override
    public void run() {
        System.out.println("[" + name + "] started");

        while (true) {
            try {
                selector.select(SELECT_TIMEOUT_MILLIS);
                processRegistrationQueue();
                processSelectedKeys();
                cleanupConnections(System.currentTimeMillis());
            } catch (IOException e) {
                System.err.println("[" + name + "] event loop error: " + e.getMessage());
            }
        }
    }

    private void processRegistrationQueue() throws IOException {
        Registration registration;

        while ((registration = registrationQueue.poll()) != null) {
            Connection connection = new Connection(registration.connectionId(), registration.channel());
            SelectionKey key = registration.channel().register(selector, SelectionKey.OP_READ, connection);

            connection.attachSelectionKey(key);
            activeConnections.put(connection.connectionId(), connection);
            metrics.recordConnectionAccepted(stats);

            System.out.println("[" + name + "] registered conn-" + connection.connectionId());
            printActiveConnectionCount();
        }
    }

    private void processSelectedKeys() {
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
        Iterator<SelectionKey> iterator = selectedKeys.iterator();

        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            iterator.remove();

            if (!key.isValid()) {
                continue;
            }

            if (key.isReadable()) {
                readAndMaybePrepareResponse(key);
            }

            if (key.isValid() && key.isWritable()) {
                writeResponse(key);
            }
        }
    }

    private void readAndMaybePrepareResponse(SelectionKey key) {
        Connection connection = (Connection) key.attachment();

        try {
            int totalBytesRead = connection.appendReadData();
            metrics.recordBytesRead(stats, totalBytesRead);

            if (totalBytesRead == -1) {
                closeConnection(key, connection, "client-closed");
                return;
            }

            if (connection.isHeaderTooLarge(MAX_HEADER_BYTES)) {
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
                HttpRequest request = requestParser.parse(rawRequest);
                connection.setCurrentRequest(request);
                metrics.recordRequest(stats);

                int requestNumber = connection.nextRequestNumber();
                System.out.println("[" + name + "] request " + request.getMethod() + " " + request.getPath()
                        + " on conn-" + connection.connectionId() + " (#" + requestNumber + ")");

                printParsedRequest(connection, connection.requestBytes(), rawRequest, request);
                HttpResponse response = router.handle(request);
                boolean keepAlive = shouldKeepAlive(request);

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

    private void writeResponse(SelectionKey key) {
        Connection connection = (Connection) key.attachment();

        try {
            boolean responseComplete = connection.writePendingResponse();
            metrics.recordBytesWritten(stats, connection.lastBytesWritten());

            if (!responseComplete) {
                key.interestOps(SelectionKey.OP_WRITE);
                return;
            }

            long startedAtNanos = connection.currentRequestStartedAtNanos();
            if (startedAtNanos > 0L) {
                metrics.recordResponseTime(System.nanoTime() - startedAtNanos);
            }

            System.out.println("[" + name + "] response complete conn-" + connection.connectionId());

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

    private void prepareInternalServerError(SelectionKey key, Connection connection) {
        if (!key.isValid()) {
            closeConnection(key, connection, "internal-error");
            return;
        }

        HttpResponse response = HttpResponse.internalServerError();
        connection.prepareResponse(response, false);
        printErrorResponse(connection, response);
        key.interestOps(SelectionKey.OP_WRITE);
    }

    private boolean shouldKeepAlive(HttpRequest request) {
        String connection = request.getHeaders().get("Connection");

        if (connection != null && "close".equalsIgnoreCase(connection.trim())) {
            return false;
        }

        return "HTTP/1.1".equalsIgnoreCase(request.getVersion());
    }

    private void printParsedRequest(
            Connection connection,
            int totalBytesRead,
            String rawRequest,
            HttpRequest request
    ) throws IOException {
        System.out.println();
        System.out.println("==================================================");
        System.out.println("[" + name + "] parsed HTTP request from " + connection.channel().getRemoteAddress());
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

    private void printSelectedRoute(
            Connection connection,
            HttpRequest request,
            HttpResponse response,
            boolean keepAlive
    ) {
        System.out.println();
        System.out.println("----- selected route -----");
        System.out.println("[" + name + "] request = " + request.getMethod() + " " + request.getPath()
                + " conn-" + connection.connectionId());
        System.out.println("[" + name + "] response = " + response.getStatusCode() + " " + response.getReasonPhrase());
        System.out.println("[" + name + "] connection = " + (keepAlive ? "keep-alive" : "close"));
        System.out.println("content-type = " + response.getHeaders().get("Content-Type"));
    }

    private void printErrorResponse(Connection connection, HttpResponse response) {
        System.out.println("[" + name + "] conn-" + connection.connectionId() + " response "
                + response.getStatusCode() + " " + response.getReasonPhrase());
    }

    private void printRawRequest(String rawRequest) {
        String visibleRawRequest = rawRequest.replace("\r", "\\r");

        System.out.println();
        System.out.println("----- raw request start -----");
        System.out.print(visibleRawRequest);
        if (!visibleRawRequest.endsWith("\n")) {
            System.out.println();
        }
        System.out.println("----- raw request end -----");
    }

    private void cleanupConnections(long now) {
        Iterator<Connection> iterator = activeConnections.values().iterator();

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
                metrics.recordConnectionClosed(stats);
            }
        }

        printActiveConnectionCount();
    }

    private void closeConnection(SelectionKey key, Connection connection, String reason) {
        key.cancel();
        connection.close(reason);
        if (activeConnections.remove(connection.connectionId()) != null) {
            metrics.recordConnectionClosed(stats);
        }
        printActiveConnectionCount();
    }

    private void printActiveConnectionCount() {
        int activeConnectionCount = activeConnections.size();

        if (activeConnectionCount != lastPrintedActiveConnectionCount) {
            System.out.println("[" + name + "] active connections=" + activeConnectionCount);
            lastPrintedActiveConnectionCount = activeConnectionCount;
        }
    }

    private record Registration(SocketChannel channel, int connectionId) {
    }
}
