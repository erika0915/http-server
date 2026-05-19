package httpserver.nio.http;

public class ConnectionContext {

    private final int id;
    private int requestCount;

    public ConnectionContext(int id) {
        this.id = id;
    }

    public String label() {
        return "[conn-" + id + "]";
    }

    public int nextRequestNumber() {
        requestCount++;
        return requestCount;
    }
}
