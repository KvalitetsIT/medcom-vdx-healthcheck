package dk.medcom.healthcheck.client;

public record Result<T>(boolean ok, String message, long responseTime, T result) {
    public Result(long responseTme, T result) {
        this(true, null, responseTme, result);
    }
}
