package com.engine.loadpulse.domain.metric;

public record LatencySample(
        long latencyMicros,
        int statusCode,
        long bytesReceived,
        boolean isError,
        String errorMessage
) {
    public static LatencySample success(long latencyMicros, int statusCode, long bytesReceived) {
        return new LatencySample(latencyMicros, statusCode, bytesReceived, false, null);
    }

    public static LatencySample error(long latencyMicros, int statusCode, String errorMessage) {
        return new LatencySample(latencyMicros, statusCode, 0L, true, errorMessage);
    }
}
