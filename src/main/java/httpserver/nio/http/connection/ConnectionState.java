package httpserver.nio.http.connection;

public enum ConnectionState {
    READING,
    WRITING,
    CLOSING,
    CLOSED
}
