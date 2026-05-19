package httpserver.nio.http.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class WorkerStats {

    private final String workerName;
    private final AtomicLong requestCount = new AtomicLong();
    private final AtomicLong bytesRead = new AtomicLong();
    private final AtomicLong bytesWritten = new AtomicLong();
    private final AtomicLong activeConnections = new AtomicLong();

    public WorkerStats(String workerName) {
        this.workerName = workerName;
    }

    public String workerName() {
        return workerName;
    }

    public void incrementRequestCount() {
        requestCount.incrementAndGet();
    }

    public void addBytesRead(long bytes) {
        bytesRead.addAndGet(bytes);
    }

    public void addBytesWritten(long bytes) {
        bytesWritten.addAndGet(bytes);
    }

    public void incrementActiveConnections() {
        activeConnections.incrementAndGet();
    }

    public void decrementActiveConnections() {
        activeConnections.updateAndGet(value -> Math.max(0, value - 1));
    }

    public long requestCount() {
        return requestCount.get();
    }

    public long bytesRead() {
        return bytesRead.get();
    }

    public long bytesWritten() {
        return bytesWritten.get();
    }

    public long activeConnections() {
        return activeConnections.get();
    }
}
