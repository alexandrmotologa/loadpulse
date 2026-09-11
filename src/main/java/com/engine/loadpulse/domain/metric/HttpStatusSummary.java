package com.engine.loadpulse.domain.metric;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class HttpStatusSummary {
    private final LongAdder status2xx = new LongAdder();
    private final LongAdder status3xx = new LongAdder();
    private final LongAdder status4xx = new LongAdder();
    private final LongAdder status5xx = new LongAdder();
    private final LongAdder timeouts = new LongAdder();
    private final LongAdder connectionErrors = new LongAdder();
    private final ConcurrentHashMap<Integer, LongAdder> statusCodes = new ConcurrentHashMap<>();

    public void recordStatus(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            status2xx.increment();
        } else if (statusCode >= 300 && statusCode < 400) {
            status3xx.increment();
        } else if (statusCode >= 400 && statusCode < 500) {
            status4xx.increment();
        } else if (statusCode >= 500) {
            status5xx.increment();
        }

        if (statusCode > 0) {
            statusCodes.computeIfAbsent(statusCode, k -> new LongAdder()).increment();
        }
    }

    public void recordTimeout() {
        timeouts.increment();
    }

    public void recordConnectionError() {
        connectionErrors.increment();
    }

    public long getStatus2xx() {
        return status2xx.sum();
    }

    public long getStatus3xx() {
        return status3xx.sum();
    }

    public long getStatus4xx() {
        return status4xx.sum();
    }

    public long getStatus5xx() {
        return status5xx.sum();
    }

    public long getTimeouts() {
        return timeouts.sum();
    }

    public long getConnectionErrors() {
        return connectionErrors.sum();
    }

    public long getTotalErrors() {
        return status4xx.sum() + status5xx.sum() + timeouts.sum() + connectionErrors.sum();
    }

    public long getTotalResponses() {
        return status2xx.sum() + status3xx.sum() + status4xx.sum() + status5xx.sum() + timeouts.sum() + connectionErrors.sum();
    }

    public double getErrorRate() {
        long total = getTotalResponses();
        if (total == 0) {
            return 0.0;
        }
        return (double) getTotalErrors() / total;
    }

    public Map<Integer, Long> getStatusCodeCounts() {
        Map<Integer, Long> counts = new java.util.TreeMap<>();
        statusCodes.forEach((code, adder) -> counts.put(code, adder.sum()));
        return Collections.unmodifiableMap(counts);
    }
}
