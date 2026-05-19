package httpserver.nio.http;

public enum ConnectionState {
    READING,
    WRITING,
    CLOSING,
    CLOSED
}
