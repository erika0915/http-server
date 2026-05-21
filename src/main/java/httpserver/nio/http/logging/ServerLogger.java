package httpserver.nio.http.logging;

import java.util.Locale;

public class ServerLogger {

    private static final LogLevel LOG_LEVEL = LogLevel.from(System.getProperty("httpserver.logLevel", "DEBUG"));
    private static final boolean METRICS_LOG_ENABLED = Boolean.parseBoolean(
            System.getProperty("httpserver.metricsLog", "true")
    );

    private ServerLogger() {
    }

    public static void info(String message) {
        if (LOG_LEVEL.allows(LogLevel.INFO)) {
            System.out.println(message);
        }
    }

    public static void debug(String message) {
        if (LOG_LEVEL.allows(LogLevel.DEBUG)) {
            System.out.println(message);
        }
    }

    public static void error(String message) {
        if (LOG_LEVEL != LogLevel.OFF) {
            System.err.println(message);
        }
    }

    public static void error(String message, Throwable throwable) {
        error(message);

        if (LOG_LEVEL.allows(LogLevel.DEBUG)) {
            throwable.printStackTrace();
        }
    }

    public static void debugBlankLine() {
        if (LOG_LEVEL.allows(LogLevel.DEBUG)) {
            System.out.println();
        }
    }

    public static void debugPrint(String message) {
        if (LOG_LEVEL.allows(LogLevel.DEBUG)) {
            System.out.print(message);
        }
    }

    public static void metrics(String message) {
        if (METRICS_LOG_ENABLED && LOG_LEVEL.allows(LogLevel.INFO)) {
            System.out.println(message);
        }
    }

    public static void metricsf(String format, Object... args) {
        if (METRICS_LOG_ENABLED && LOG_LEVEL.allows(LogLevel.INFO)) {
            System.out.printf(Locale.US, format, args);
        }
    }

    public static boolean isDebugEnabled() {
        return LOG_LEVEL.allows(LogLevel.DEBUG);
    }

    private enum LogLevel {
        OFF(0),
        INFO(1),
        DEBUG(2);

        private final int priority;

        LogLevel(int priority) {
            this.priority = priority;
        }

        private boolean allows(LogLevel target) {
            return priority >= target.priority;
        }

        private static LogLevel from(String value) {
            for (LogLevel level : values()) {
                if (level.name().equalsIgnoreCase(value)) {
                    return level;
                }
            }

            return DEBUG;
        }
    }
}
