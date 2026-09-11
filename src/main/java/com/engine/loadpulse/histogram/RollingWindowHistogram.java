package com.engine.loadpulse.histogram;

import org.HdrHistogram.Histogram;

import java.util.ArrayDeque;
import java.util.Deque;

public class RollingWindowHistogram {
    private static final long HIGHEST_TRACKABLE_VALUE = 3_600_000_000L;
    private static final int NUMBER_OF_SIGNIFICANT_VALUE_DIGITS = 3;

    private final int windowSize;
    private final Deque<Histogram> window;
    private final Deque<Long> byteCounts;
    private Histogram currentWindowAggregate;

    public RollingWindowHistogram(int windowSizeSeconds) {
        this.windowSize = Math.max(1, windowSizeSeconds);
        this.window = new ArrayDeque<>(windowSize);
        this.byteCounts = new ArrayDeque<>(windowSize);
        this.currentWindowAggregate = new Histogram(1L, HIGHEST_TRACKABLE_VALUE, NUMBER_OF_SIGNIFICANT_VALUE_DIGITS);
    }

    public synchronized void addInterval(Histogram interval, long intervalBytes) {
        if (interval == null) {
            return;
        }

        if (window.size() >= windowSize) {
            window.pollFirst();
            byteCounts.pollFirst();
        }

        window.addLast(interval.copy());
        byteCounts.addLast(intervalBytes);

        recomputeAggregate();
    }

    private void recomputeAggregate() {
        currentWindowAggregate.reset();
        for (Histogram h : window) {
            currentWindowAggregate.add(h);
        }
    }

    public synchronized long getCurrentCount() {
        return currentWindowAggregate.getTotalCount();
    }

    public synchronized double getCurrentRps() {
        if (window.isEmpty()) {
            return 0.0;
        }
        return (double) currentWindowAggregate.getTotalCount() / window.size();
    }

    public synchronized double getCurrentThroughputBytesPerSec() {
        if (byteCounts.isEmpty()) {
            return 0.0;
        }
        long sum = 0;
        for (Long b : byteCounts) {
            sum += b;
        }
        return (double) sum / byteCounts.size();
    }

    public synchronized long getP50Micros() {
        return currentWindowAggregate.getTotalCount() > 0 ? currentWindowAggregate.getValueAtPercentile(50.0) : 0;
    }

    public synchronized long getP90Micros() {
        return currentWindowAggregate.getTotalCount() > 0 ? currentWindowAggregate.getValueAtPercentile(90.0) : 0;
    }

    public synchronized long getP99Micros() {
        return currentWindowAggregate.getTotalCount() > 0 ? currentWindowAggregate.getValueAtPercentile(99.0) : 0;
    }

    public synchronized Histogram getAggregate() {
        return currentWindowAggregate.copy();
    }
}
