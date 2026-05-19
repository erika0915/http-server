package httpserver.nio.http;

import httpserver.nio.http.request.HttpRequest;
import httpserver.nio.http.response.HttpResponse;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class Connection {

    private static final int BUFFER_SIZE = 32 * 1024;

    private final int connectionId;
    private final SocketChannel channel;
    private final ByteBuffer readBuffer;

    private ByteBuffer writeBuffer;
    private ConnectionState state;
    private boolean keepAlive;
    private final long connectedAt;
    private long lastActiveAt;

    private HttpResponse pendingResponse;
    private HttpRequest currentRequest;
    private int requestCount;

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

    public SocketChannel channel() {
        return channel;
    }

    public ConnectionState state() {
        return state;
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
     * SocketChannel에서 읽은 데이터를 이 Connection의 readBuffer에 추가합니다.
     *
     * <p>아직 partial read를 완성하지 않는 단계이므로, 요청 하나가 처리될 때마다
     * readBuffer를 비우고 현재 도착한 요청 하나를 담는 단순 구조를 유지합니다.
     */
    public int appendReadData() throws IOException {
        updateLastActive();
        readBuffer.clear();

        int totalBytesRead = 0;

        while (readBuffer.hasRemaining()) {
            int bytesRead = channel.read(readBuffer);

            if (bytesRead == -1) {
                return -1;
            }

            if (bytesRead == 0) {
                break;
            }

            totalBytesRead += bytesRead;

            if (containsHeaderEnd()) {
                break;
            }
        }

        return totalBytesRead;
    }

    public String readRequestText() {
        readBuffer.flip();

        return StandardCharsets.UTF_8
                .decode(readBuffer)
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
     * 준비된 응답을 channel에 씁니다.
     *
     * <p>아직 partial write를 완성하지 않으므로 현재 단계에서는 작은 응답이
     * 이 루프 안에서 모두 써진다고 가정합니다.
     */
    public void writePendingResponse() throws IOException {
        updateLastActive();

        while (writeBuffer.hasRemaining()) {
            channel.write(writeBuffer);
        }

        System.out.println(label() + " response sent");
    }

    public void readyToReadNextRequest() {
        currentRequest = null;
        pendingResponse = null;
        readBuffer.clear();
        writeBuffer = ByteBuffer.allocate(0);
        transitionTo(ConnectionState.READING);
    }

    public void close() {
        try {
            transitionTo(ConnectionState.CLOSED);
            channel.close();
            System.out.println(label() + " closed");
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
