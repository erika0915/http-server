package httpserver.nio.http.metrics;

import httpserver.nio.http.logging.ServerLogger;

public class MetricsReporter implements Runnable {

    private static final long REPORT_INTERVAL_MILLIS = 5_000;

    private final ServerMetrics metrics;

    public MetricsReporter(ServerMetrics metrics) {
        this.metrics = metrics;
    }

    public void start() {
        Thread thread = new Thread(this, "metrics-reporter");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(REPORT_INTERVAL_MILLIS);
                printReport();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void printReport() {
        ServerLogger.metrics("");
        ServerLogger.metrics("========== SERVER METRICS ==========");
        ServerLogger.metrics("uptime=" + metrics.uptimeSeconds() + "s");
        ServerLogger.metrics("totalRequests=" + metrics.totalRequests());
        ServerLogger.metricsf("requestsPerSecond=%.3f%n", metrics.requestsPerSecond());
        ServerLogger.metrics("activeConnections=" + metrics.activeConnections());
        ServerLogger.metrics("totalConnections=" + metrics.totalConnections());
        ServerLogger.metrics("bytesRead=" + metrics.bytesRead());
        ServerLogger.metrics("bytesWritten=" + metrics.bytesWritten());
        ServerLogger.metricsf("averageResponseTimeMillis=%.3f%n", metrics.averageResponseTimeMillis());
        ServerLogger.metricsf("maxResponseTimeMillis=%.3f%n", metrics.maxResponseTimeMillis());

        for (WorkerStats stats : metrics.allWorkerStats()) {
            ServerLogger.metrics(stats.workerName()
                    + " requests=" + stats.requestCount()
                    + " activeConnections=" + stats.activeConnections()
                    + " bytesRead=" + stats.bytesRead()
                    + " bytesWritten=" + stats.bytesWritten());
        }

        ServerLogger.metrics("====================================");
        ServerLogger.metrics("");
    }
}
