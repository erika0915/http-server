package httpserver.nio.http.metrics;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ServerMetrics {

    private static final ServerMetrics GLOBAL = new ServerMetrics();

    private final long startedAtNanos = System.nanoTime();
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong activeConnections = new AtomicLong();
    private final AtomicLong totalConnections = new AtomicLong();
    private final AtomicLong bytesRead = new AtomicLong();
    private final AtomicLong bytesWritten = new AtomicLong();
    private final AtomicLong totalResponseTimeNanos = new AtomicLong();
    private final AtomicLong maxResponseTimeNanos = new AtomicLong();
    private final Map<String, WorkerStats> workerStats = new ConcurrentHashMap<>();

    private ServerMetrics() {
    }

    public static ServerMetrics global() {
        return GLOBAL;
    }

    public WorkerStats workerStats(String workerName) {
        return workerStats.computeIfAbsent(workerName, WorkerStats::new);
    }

    public void recordConnectionAccepted(WorkerStats stats) {
        totalConnections.incrementAndGet();
        activeConnections.incrementAndGet();
        stats.incrementActiveConnections();
    }

    public void recordConnectionClosed(WorkerStats stats) {
        activeConnections.updateAndGet(value -> Math.max(0, value - 1));
        stats.decrementActiveConnections();
    }

    public void recordRequest(WorkerStats stats) {
        totalRequests.incrementAndGet();
        stats.incrementRequestCount();
    }

    public void recordBytesRead(WorkerStats stats, long bytes) {
        if (bytes <= 0) {
            return;
        }

        bytesRead.addAndGet(bytes);
        stats.addBytesRead(bytes);
    }

    public void recordBytesWritten(WorkerStats stats, long bytes) {
        if (bytes <= 0) {
            return;
        }

        bytesWritten.addAndGet(bytes);
        stats.addBytesWritten(bytes);
    }

    public void recordResponseTime(long responseTimeNanos) {
        if (responseTimeNanos <= 0) {
            return;
        }

        totalResponseTimeNanos.addAndGet(responseTimeNanos);
        maxResponseTimeNanos.updateAndGet(previous -> Math.max(previous, responseTimeNanos));
    }

    public long uptimeSeconds() {
        return (System.nanoTime() - startedAtNanos) / 1_000_000_000L;
    }

    public double requestsPerSecond() {
        long uptime = uptimeSeconds();
        if (uptime == 0) {
            return 0.0;
        }

        return (double) totalRequests() / uptime;
    }

    public double averageResponseTimeMillis() {
        long requests = totalRequests();
        if (requests == 0) {
            return 0.0;
        }

        return totalResponseTimeNanos.get() / 1_000_000.0 / requests;
    }

    public double maxResponseTimeMillis() {
        return maxResponseTimeNanos.get() / 1_000_000.0;
    }

    public long totalRequests() {
        return totalRequests.get();
    }

    public long activeConnections() {
        return activeConnections.get();
    }

    public long totalConnections() {
        return totalConnections.get();
    }

    public long bytesRead() {
        return bytesRead.get();
    }

    public long bytesWritten() {
        return bytesWritten.get();
    }

    public Collection<WorkerStats> allWorkerStats() {
        return workerStats.values()
                .stream()
                .sorted(Comparator.comparing(WorkerStats::workerName))
                .toList();
    }
}
