package httpserver.nio.http.metrics;

import java.util.Locale;

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
        System.out.println();
        System.out.println("========== SERVER METRICS ==========");
        System.out.println("uptime=" + metrics.uptimeSeconds() + "s");
        System.out.println("totalRequests=" + metrics.totalRequests());
        System.out.printf(Locale.US, "requestsPerSecond=%.3f%n", metrics.requestsPerSecond());
        System.out.println("activeConnections=" + metrics.activeConnections());
        System.out.println("totalConnections=" + metrics.totalConnections());
        System.out.println("bytesRead=" + metrics.bytesRead());
        System.out.println("bytesWritten=" + metrics.bytesWritten());
        System.out.printf(Locale.US, "averageResponseTimeMillis=%.3f%n", metrics.averageResponseTimeMillis());
        System.out.printf(Locale.US, "maxResponseTimeMillis=%.3f%n", metrics.maxResponseTimeMillis());

        for (WorkerStats stats : metrics.allWorkerStats()) {
            System.out.println(stats.workerName()
                    + " requests=" + stats.requestCount()
                    + " activeConnections=" + stats.activeConnections()
                    + " bytesRead=" + stats.bytesRead()
                    + " bytesWritten=" + stats.bytesWritten());
        }

        System.out.println("====================================");
        System.out.println();
    }
}
