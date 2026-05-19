package httpserver.nio.http.metrics;

import httpserver.nio.http.response.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MetricsHandler {

    private final ServerMetrics metrics;

    public MetricsHandler(ServerMetrics metrics) {
        this.metrics = metrics;
    }

    public HttpResponse handle() {
        String body = renderPrometheusTextFormat();
        return HttpResponse.okBytes(
                "text/plain; version=0.0.4",
                body.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String renderPrometheusTextFormat() {
        StringBuilder body = new StringBuilder();

        appendHelp(body, "nio_http_total_requests", "Total number of parsed HTTP requests.");
        appendCounter(body, "nio_http_total_requests", metrics.totalRequests());

        appendHelp(body, "nio_http_active_connections", "Current active TCP connections.");
        appendGauge(body, "nio_http_active_connections", metrics.activeConnections());

        appendHelp(body, "nio_http_total_connections", "Total accepted TCP connections.");
        appendCounter(body, "nio_http_total_connections", metrics.totalConnections());

        appendHelp(body, "nio_http_bytes_read", "Total bytes read from SocketChannel.");
        appendCounter(body, "nio_http_bytes_read", metrics.bytesRead());

        appendHelp(body, "nio_http_bytes_written", "Total bytes written to SocketChannel.");
        appendCounter(body, "nio_http_bytes_written", metrics.bytesWritten());

        appendHelp(body, "nio_http_uptime_seconds", "Server uptime in seconds.");
        appendGauge(body, "nio_http_uptime_seconds", metrics.uptimeSeconds());

        appendHelp(body, "nio_http_requests_per_second", "Average requests per second since server start.");
        appendGauge(body, "nio_http_requests_per_second", metrics.requestsPerSecond());

        appendHelp(body, "nio_http_response_time_average_millis", "Average response time in milliseconds.");
        appendGauge(body, "nio_http_response_time_average_millis", metrics.averageResponseTimeMillis());

        appendHelp(body, "nio_http_response_time_max_millis", "Maximum observed response time in milliseconds.");
        appendGauge(body, "nio_http_response_time_max_millis", metrics.maxResponseTimeMillis());

        appendHelp(body, "nio_http_worker_requests", "Total requests handled by each worker.");
        body.append("# TYPE nio_http_worker_requests counter\n");
        for (WorkerStats stats : metrics.allWorkerStats()) {
            body.append("nio_http_worker_requests{worker=\"")
                    .append(stats.workerName())
                    .append("\"} ")
                    .append(stats.requestCount())
                    .append('\n');
        }

        appendHelp(body, "nio_http_worker_active_connections", "Active connections assigned to each worker.");
        body.append("# TYPE nio_http_worker_active_connections gauge\n");
        for (WorkerStats stats : metrics.allWorkerStats()) {
            body.append("nio_http_worker_active_connections{worker=\"")
                    .append(stats.workerName())
                    .append("\"} ")
                    .append(stats.activeConnections())
                    .append('\n');
        }

        return body.toString();
    }

    private void appendHelp(StringBuilder body, String name, String help) {
        body.append("# HELP ").append(name).append(' ').append(help).append('\n');
    }

    private void appendCounter(StringBuilder body, String name, long value) {
        body.append("# TYPE ").append(name).append(" counter\n");
        body.append(name).append(' ').append(value).append('\n');
    }

    private void appendGauge(StringBuilder body, String name, long value) {
        body.append("# TYPE ").append(name).append(" gauge\n");
        body.append(name).append(' ').append(value).append('\n');
    }

    private void appendGauge(StringBuilder body, String name, double value) {
        body.append("# TYPE ").append(name).append(" gauge\n");
        body.append(name).append(' ').append(String.format(Locale.US, "%.3f", value)).append('\n');
    }
}
