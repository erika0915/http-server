package httpserver.nio.http.connection;

import httpserver.nio.http.request.HttpRequest;
import httpserver.nio.http.response.HttpResponse;
import httpserver.nio.http.logging.ServerLogger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class Connection {

    private static final int BUFFER_SIZE = 32 * 1024;

    private final int connectionId;
    private final SocketChannel channel;
    private final ByteBuffer readBuffer;

    private ByteBuffer writeBuffer;
    private SelectionKey selectionKey;
    private ConnectionState state;
    private boolean keepAlive;
    private final long connectedAt;
    private long lastActiveAt;

    private HttpResponse pendingResponse;
    private HttpRequest currentRequest;
    private int requestCount;
    private long currentRequestStartedAtNanos;
    private int lastBytesWritten;

    public Connection(int connectionId, SocketChannel channel) {
        this.connectionId = connectionId;
        this.channel = channel;
        this.readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        this.writeBuffer = ByteBuffer.allocate(0);
        this.keepAlive = false;
        this.connectedAt = System.currentTimeMillis();
        this.lastActiveAt = connectedAt;
        this.state = ConnectionState.READING;

        ServerLogger.debug(label() + " connected");
        ServerLogger.debug(label() + " state " + state);
    }

    public String label() {
        return "[conn-" + connectionId + "]";
    }

    public int connectionId() {
        return connectionId;
    }

    public SocketChannel channel() {
        return channel;
    }

    public void attachSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
    }

    public SelectionKey selectionKey() {
        return selectionKey;
    }

    public ConnectionState state() {
        return state;
    }

    public boolean isClosed() {
        return state == ConnectionState.CLOSED;
    }

    public boolean isIdleTimeout(long now, long timeoutMillis) {
        return !isClosed() && now - lastActiveAt >= timeoutMillis;
    }

    public boolean keepAlive() {
        return keepAlive;
    }

    public int nextRequestNumber() {
        requestCount++;
        return requestCount;
    }

    public int requestCount() {
        return requestCount;
    }

    public long connectedAt() {
        return connectedAt;
    }

    public long lastActiveAt() {
        return lastActiveAt;
    }

    public void updateLastActive() {
        lastActiveAt = System.currentTimeMillis();
    }

    /**
     * SocketChannel에서 지금 도착해 있는 바이트를 readBuffer 뒤쪽에 이어 붙입니다.
     *
     * <p>이전 단계와 가장 큰 차이는 readBuffer.clear()를 먼저 호출하지 않는다는 점입니다.
     * HTTP 요청이 여러 TCP 조각으로 나뉘어 들어올 수 있으므로, \r\n\r\n을 찾기 전까지는
     * 기존에 읽어 둔 바이트를 보존해야 합니다.
     *
     * <p>Non-Blocking 모드의 read()는 다음 세 가지 값을 반환할 수 있습니다.
     * - 양수: 실제로 읽은 바이트 수
     * - 0: 지금 당장 읽을 데이터가 없음
     * - -1: 클라이언트가 연결을 종료함
     */
    public int appendReadData() throws IOException {
        updateLastActive();

        int totalBytesRead = 0;

        while (readBuffer.hasRemaining()) {
            int bytesRead = channel.read(readBuffer);

            if (bytesRead == -1) {
                return -1;
            }

            ServerLogger.debug(label() + " read " + bytesRead + " bytes");

            if (bytesRead == 0) {
                break;
            }

            if (currentRequestStartedAtNanos == 0L) {
                currentRequestStartedAtNanos = System.nanoTime();
            }

            totalBytesRead += bytesRead;

            if (isRequestComplete()) {
                break;
            }
        }

        return totalBytesRead;
    }

    public boolean isRequestComplete() {
        int headerEndIndex = findHeaderEndIndex();

        if (headerEndIndex < 0) {
            return false;
        }

        String headerText = readText(0, headerEndIndex);
        int bodyStartIndex = headerEndIndex + 4;

        if (isChunkedRequest(headerText)) {
            return isChunkedBodyComplete(bodyStartIndex);
        }

        Integer contentLength = parseContentLength(headerText);

        if (contentLength == null) {
            return true;
        }

        return readBuffer.position() >= bodyStartIndex + contentLength;
    }

    public boolean isHeaderTooLarge(int maxHeaderBytes) {
        return findHeaderEndIndex() < 0 && readBuffer.position() > maxHeaderBytes;
    }

    /*
     * readBuffer가 꽉 찼는데도 \r\n\r\n을 찾지 못했다면,
     * 지금 단계의 단순 서버는 더 이상 요청을 누적할 공간이 없습니다.
     */
    public boolean canReadMore() {
        return readBuffer.hasRemaining();
    }

    /*
     * 현재까지 누적된 요청 바이트 수입니다.
     * partial read 테스트에서는 마지막 read() 크기보다 이 값이 더 중요합니다.
     */
    public int requestBytes() {
        return readBuffer.position();
    }

    public long currentRequestStartedAtNanos() {
        return currentRequestStartedAtNanos;
    }

    public String readRequestText() {
        /*
         * parse를 위해 buffer를 읽기 모드로 바꾸되, 원본 readBuffer의 position은
         * 유지합니다. 원본을 flip() 해버리면 다음 상태 전환에서 buffer를 관리하기
         * 어려워지기 때문입니다.
         */
        ByteBuffer readOnlyBuffer = readBuffer.asReadOnlyBuffer();
        readOnlyBuffer.flip();

        return StandardCharsets.UTF_8
                .decode(readOnlyBuffer)
                .toString();
    }

    public void setCurrentRequest(HttpRequest currentRequest) {
        this.currentRequest = currentRequest;
    }

    public HttpRequest currentRequest() {
        return currentRequest;
    }

    /**
     * Router가 만든 HttpResponse를 쓰기 준비 상태로 만듭니다.
     */
    public void prepareResponse(HttpResponse response, boolean keepAlive) {
        this.keepAlive = keepAlive;
        this.pendingResponse = response;
        this.pendingResponse.setConnection(keepAlive);
        this.writeBuffer = ByteBuffer.wrap(response.toBytes());
        transitionTo(ConnectionState.WRITING);
    }

    /**
     * writeBuffer에 담긴 응답 바이트를 SocketChannel로 한 번 써 봅니다.
     *
     * <p>Non-Blocking write()는 응답 전체를 한 번에 전송하지 못할 수 있습니다.
     * 그래서 while로 끝까지 밀어 넣지 않고, 이번 이벤트에서 쓸 수 있는 만큼만 쓴 뒤
     * 남은 데이터가 있으면 false를 반환합니다. HttpServer는 false를 받으면 OP_WRITE를
     * 유지해서 다음 쓰기 가능 이벤트에서 이어 씁니다.
     */
    public boolean writePendingResponse() throws IOException {
        updateLastActive();

        int bytesWritten = channel.write(writeBuffer);
        lastBytesWritten = bytesWritten;
        ServerLogger.debug(label() + " wrote " + bytesWritten + " bytes");

        if (writeBuffer.hasRemaining()) {
            ServerLogger.debug(label() + " write incomplete, remaining=" + writeBuffer.remaining());
            return false;
        }

        ServerLogger.debug(label() + " response complete");
        return true;
    }

    public void readyToReadNextRequest() {
        currentRequest = null;
        pendingResponse = null;
        currentRequestStartedAtNanos = 0L;
        lastBytesWritten = 0;
        readBuffer.clear();
        writeBuffer = ByteBuffer.allocate(0);
        transitionTo(ConnectionState.READING);
    }

    public int lastBytesWritten() {
        return lastBytesWritten;
    }

    public void close() {
        close("normal");
    }

    public void close(String reason) {
        try {
            transitionTo(ConnectionState.CLOSING);
            transitionTo(ConnectionState.CLOSED);
            channel.close();
            ServerLogger.debug(label() + " closed reason=" + reason);
        } catch (IOException e) {
            ServerLogger.error(label() + " error while closing: " + e.getMessage());
        }
    }

    private void transitionTo(ConnectionState nextState) {
        if (state == nextState) {
            ServerLogger.debug(label() + " state " + state);
            return;
        }

        ServerLogger.debug(label() + " state " + state + " -> " + nextState);
        state = nextState;
    }

    private int findHeaderEndIndex() {
        int end = readBuffer.position();

        for (int i = 0; i <= end - 4; i++) {
            if (readBuffer.get(i) == '\r'
                    && readBuffer.get(i + 1) == '\n'
                    && readBuffer.get(i + 2) == '\r'
                    && readBuffer.get(i + 3) == '\n') {
                return i;
            }
        }

        return -1;
    }

    private String readText(int start, int end) {
        ByteBuffer readOnlyBuffer = readBuffer.asReadOnlyBuffer();
        readOnlyBuffer.position(start);
        readOnlyBuffer.limit(end);

        return StandardCharsets.UTF_8
                .decode(readOnlyBuffer)
                .toString();
    }

    private Integer parseContentLength(String headerText) {
        String[] lines = headerText.split("\r\n");

        for (String line : lines) {
            int colonIndex = line.indexOf(':');

            if (colonIndex <= 0) {
                continue;
            }

            String headerName = line.substring(0, colonIndex).trim();

            if (!"Content-Length".equalsIgnoreCase(headerName)) {
                continue;
            }

            String headerValue = line.substring(colonIndex + 1).trim();

            try {
                int contentLength = Integer.parseInt(headerValue);
                return Math.max(contentLength, 0);
            } catch (NumberFormatException e) {
                /*
                 * 잘못된 Content-Length 값은 parser 단계에서 400 Bad Request로 처리합니다.
                 * 여기서는 요청이 더 오기를 무한히 기다리지 않도록 header까지만 완성된 것으로 봅니다.
                 */
                return null;
            }
        }

        return null;
    }

    private boolean isChunkedRequest(String headerText) {
        String[] lines = headerText.split("\r\n");

        for (String line : lines) {
            int colonIndex = line.indexOf(':');

            if (colonIndex <= 0) {
                continue;
            }

            String headerName = line.substring(0, colonIndex).trim();
            String headerValue = line.substring(colonIndex + 1).trim();

            if ("Transfer-Encoding".equalsIgnoreCase(headerName)
                    && "chunked".equalsIgnoreCase(headerValue)) {
                return true;
            }
        }

        return false;
    }

    private boolean isChunkedBodyComplete(int bodyStartIndex) {
        String bodyText = readText(bodyStartIndex, readBuffer.position());

        /*
         * Step 16의 학습용 구현에서는 trailer field 없는 가장 단순한 chunked body를 처리합니다.
         * 마지막 chunk는 0\r\n\r\n 형태로 끝납니다.
         */
        return bodyText.startsWith("0\r\n\r\n") || bodyText.contains("\r\n0\r\n\r\n");
    }
}
