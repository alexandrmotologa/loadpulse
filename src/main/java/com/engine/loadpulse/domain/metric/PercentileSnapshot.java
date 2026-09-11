package com.engine.loadpulse.domain.metric;

import java.util.Map;

public record PercentileSnapshot(
        long totalRequests,
        double durationSeconds,
        double requestsPerSecond,
        double throughputBytesPerSecond,
        long minLatencyMicros,
        long maxLatencyMicros,
        double meanLatencyMicros,
        double stdDeviationMicros,
        long p50Micros,
        long p75Micros,
        long p90Micros,
        long p95Micros,
        long p99Micros,
        long p999Micros,
        long p9999Micros,
        long status2xx,
        long status3xx,
        long status4xx,
        long status5xx,
        long timeouts,
        long connectionErrors,
        double errorRate,
        Map<Integer, Long> statusCodeCounts,
        Map<String, Long> histogramBuckets
) {
    public double minLatencyMillis() {
        return minLatencyMicros / 1000.0;
    }

    public double maxLatencyMillis() {
        return maxLatencyMicros / 1000.0;
    }

    public double meanLatencyMillis() {
        return meanLatencyMicros / 1000.0;
    }

    public double p50Millis() {
        return p50Micros / 1000.0;
    }

    public double p75Millis() {
        return p75Micros / 1000.0;
    }

    public double p90Millis() {
        return p90Micros / 1000.0;
    }

    public double p95Millis() {
        return p95Micros / 1000.0;
    }

    public double p99Millis() {
        return p99Micros / 1000.0;
    }

    public double p999Millis() {
        return p999Micros / 1000.0;
    }

    public double p9999Millis() {
        return p9999Micros / 1000.0;
    }

    public double throughputMbps() {
        return (throughputBytesPerSecond * 8) / (1024.0 * 1024.0);
    }
}
