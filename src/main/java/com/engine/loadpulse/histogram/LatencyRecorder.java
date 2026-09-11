package com.engine.loadpulse.histogram;

import com.engine.loadpulse.domain.metric.HttpStatusSummary;
import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

public class LatencyRecorder {
    // 1 microsecond to 1 hour with 3 significant decimal digits
    private static final long HIGHEST_TRACKABLE_VALUE = 3_600_000_000L;
    private static final int NUMBER_OF_SIGNIFICANT_VALUE_DIGITS = 3;

    private final Recorder recorder;
    private final HttpStatusSummary httpStatusSummary;
    private final LongAdder totalBytes = new LongAdder();
    private Histogram cumulativeHistogram;

    public LatencyRecorder() {
        this.recorder = new Recorder(1L, HIGHEST_TRACKABLE_VALUE, NUMBER_OF_SIGNIFICANT_VALUE_DIGITS);
        this.httpStatusSummary = new HttpStatusSummary();
        this.cumulativeHistogram = new Histogram(1L, HIGHEST_TRACKABLE_VALUE, NUMBER_OF_SIGNIFICANT_VALUE_DIGITS);
    }

    public void recordLatency(long latencyMicros) {
        if (latencyMicros <= 0) {
            latencyMicros = 1;
        } else if (latencyMicros > HIGHEST_TRACKABLE_VALUE) {
            latencyMicros = HIGHEST_TRACKABLE_VALUE;
        }
        recorder.recordValue(latencyMicros);
    }

    public void recordLatencyWithExpectedInterval(long latencyMicros, long expectedIntervalMicros) {
        if (latencyMicros <= 0) {
            latencyMicros = 1;
        } else if (latencyMicros > HIGHEST_TRACKABLE_VALUE) {
            latencyMicros = HIGHEST_TRACKABLE_VALUE;
        }

        if (expectedIntervalMicros > 0) {
            recorder.recordValueWithExpectedInterval(latencyMicros, expectedIntervalMicros);
        } else {
            recorder.recordValue(latencyMicros);
        }
    }

    public void recordStatus(int statusCode) {
        httpStatusSummary.recordStatus(statusCode);
    }

    public void recordTimeout() {
        httpStatusSummary.recordTimeout();
    }

    public void recordConnectionError() {
        httpStatusSummary.recordConnectionError();
    }

    public void recordBytes(long bytes) {
        if (bytes > 0) {
            totalBytes.add(bytes);
        }
    }

    public HttpStatusSummary getHttpStatusSummary() {
        return httpStatusSummary;
    }

    public synchronized Histogram getIntervalHistogram() {
        return recorder.getIntervalHistogram();
    }

    public synchronized Histogram getCumulativeHistogram() {
        Histogram interval = recorder.getIntervalHistogram();
        cumulativeHistogram.add(interval);
        return cumulativeHistogram.copy();
    }

    public synchronized PercentileSnapshot createSnapshot(double elapsedSeconds) {
        Histogram snapshot = getCumulativeHistogram();
        long totalReqs = snapshot.getTotalCount();
        double rps = elapsedSeconds > 0 ? totalReqs / elapsedSeconds : 0.0;
        double throughputBps = elapsedSeconds > 0 ? (double) totalBytes.sum() / elapsedSeconds : 0.0;

        long min = totalReqs > 0 ? snapshot.getMinValue() : 0;
        long max = totalReqs > 0 ? snapshot.getMaxValue() : 0;
        double mean = totalReqs > 0 ? snapshot.getMean() : 0.0;
        double stdDev = totalReqs > 0 ? snapshot.getStdDeviation() : 0.0;

        long p50 = totalReqs > 0 ? snapshot.getValueAtPercentile(50.0) : 0;
        long p75 = totalReqs > 0 ? snapshot.getValueAtPercentile(75.0) : 0;
        long p90 = totalReqs > 0 ? snapshot.getValueAtPercentile(90.0) : 0;
        long p95 = totalReqs > 0 ? snapshot.getValueAtPercentile(95.0) : 0;
        long p99 = totalReqs > 0 ? snapshot.getValueAtPercentile(99.0) : 0;
        long p999 = totalReqs > 0 ? snapshot.getValueAtPercentile(99.9) : 0;
        long p9999 = totalReqs > 0 ? snapshot.getValueAtPercentile(99.99) : 0;

        Map<String, Long> buckets = computeDistributionBuckets(snapshot);

        return new PercentileSnapshot(
                totalReqs,
                elapsedSeconds,
                rps,
                throughputBps,
                min,
                max,
                mean,
                stdDev,
                p50,
                p75,
                p90,
                p95,
                p99,
                p999,
                p9999,
                httpStatusSummary.getStatus2xx(),
                httpStatusSummary.getStatus3xx(),
                httpStatusSummary.getStatus4xx(),
                httpStatusSummary.getStatus5xx(),
                httpStatusSummary.getTimeouts(),
                httpStatusSummary.getConnectionErrors(),
                httpStatusSummary.getErrorRate(),
                httpStatusSummary.getStatusCodeCounts(),
                buckets
        );
    }

    public static Map<String, Long> computeDistributionBuckets(Histogram histogram) {
        Map<String, Long> buckets = new LinkedHashMap<>();
        if (histogram == null || histogram.getTotalCount() == 0) {
            buckets.put("0-1ms", 0L);
            buckets.put("1-5ms", 0L);
            buckets.put("5-15ms", 0L);
            buckets.put("15-50ms", 0L);
            buckets.put("50-100ms", 0L);
            buckets.put("100-250ms", 0L);
            buckets.put("250-500ms", 0L);
            buckets.put("500ms-1s", 0L);
            buckets.put("1s+", 0L);
            return buckets;
        }

        // thresholds in microseconds
        long c1 = histogram.getCountBetweenValues(0L, 1_000L);
        long c5 = histogram.getCountBetweenValues(1_001L, 5_000L);
        long c15 = histogram.getCountBetweenValues(5_001L, 15_000L);
        long c50 = histogram.getCountBetweenValues(15_001L, 50_000L);
        long c100 = histogram.getCountBetweenValues(50_001L, 100_000L);
        long c250 = histogram.getCountBetweenValues(100_001L, 250_000L);
        long c500 = histogram.getCountBetweenValues(250_001L, 500_000L);
        long c1000 = histogram.getCountBetweenValues(500_001L, 1_000_000L);
        long cOver = histogram.getCountBetweenValues(1_000_001L, HIGHEST_TRACKABLE_VALUE);

        buckets.put("0-1ms", c1);
        buckets.put("1-5ms", c5);
        buckets.put("5-15ms", c15);
        buckets.put("15-50ms", c50);
        buckets.put("50-100ms", c100);
        buckets.put("100-250ms", c250);
        buckets.put("250-500ms", c500);
        buckets.put("500ms-1s", c1000);
        buckets.put("1s+", cOver);

        return buckets;
    }

    public synchronized void reset() {
        recorder.reset();
        cumulativeHistogram.reset();
        totalBytes.reset();
    }
}
