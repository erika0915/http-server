package httpserver.nio.http.connection;

import httpserver.nio.http.request.HttpRequest;
import httpserver.nio.http.response.HttpResponse;

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

        System.out.println(label() + " connected");
        System.out.println(label() + " state " + state);
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

            System.out.println(label() + " read " + bytesRead + " bytes");

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
        return containsHeaderEnd();
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
        System.out.println(label() + " wrote " + bytesWritten + " bytes");

        if (writeBuffer.hasRemaining()) {
            System.out.println(label() + " write incomplete, remaining=" + writeBuffer.remaining());
            return false;
        }

        System.out.println(label() + " response complete");
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
            System.out.println(label() + " closed reason=" + reason);
        } catch (IOException e) {
            System.err.println(label() + " error while closing: " + e.getMessage());
        }
    }

    private void transitionTo(ConnectionState nextState) {
        if (state == nextState) {
            System.out.println(label() + " state " + state);
            return;
        }

        System.out.println(label() + " state " + state + " -> " + nextState);
        state = nextState;
    }

    private boolean containsHeaderEnd() {
        int end = readBuffer.position();

        for (int i = 0; i <= end - 4; i++) {
            if (readBuffer.get(i) == '\r'
                    && readBuffer.get(i + 1) == '\n'
                    && readBuffer.get(i + 2) == '\r'
                    && readBuffer.get(i + 3) == '\n') {
                return true;
            }
        }

        return false;
    }
}
