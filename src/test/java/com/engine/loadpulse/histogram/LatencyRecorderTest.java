package com.engine.loadpulse.histogram;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LatencyRecorderTest {

    @Test
    @DisplayName("Should accurately record latencies and calculate percentiles")
    void testLatencyRecordingAndPercentiles() {
        LatencyRecorder recorder = new LatencyRecorder();

        // Record 100 samples with varying latencies: 1ms to 100ms (1,000 to 100,000 micros)
        for (int i = 1; i <= 100; i++) {
            recorder.recordLatency(i * 1000L);
            recorder.recordStatus(200);
            recorder.recordBytes(256);
        }

        PercentileSnapshot snapshot = recorder.createSnapshot(1.0);

        assertThat(snapshot.totalRequests()).isEqualTo(100);
        assertThat(snapshot.status2xx()).isEqualTo(100);
        assertThat(snapshot.status5xx()).isEqualTo(0);
        assertThat(snapshot.errorRate()).isEqualTo(0.0);

        // p50 should be ~50ms (50,000 micros)
        assertThat(snapshot.p50Millis()).isBetween(48.0, 52.0);
        // p90 should be ~90ms
        assertThat(snapshot.p90Millis()).isBetween(88.0, 92.0);
        // p99 should be ~99ms
        assertThat(snapshot.p99Millis()).isBetween(97.0, 101.0);

        // Check distribution buckets
        assertThat(snapshot.histogramBuckets()).isNotEmpty();
        assertThat(snapshot.histogramBuckets().get("0-1ms")).isEqualTo(1); // 1,000 micros
        assertThat(snapshot.histogramBuckets().get("1-5ms")).isEqualTo(4); // 2k, 3k, 4k, 5k
    }

    @Test
    @DisplayName("Should compensate for Coordinated Omission when expected interval is given")
    void testCoordinatedOmissionCorrection() {
        LatencyRecorder uncorrected = new LatencyRecorder();
        LatencyRecorder corrected = new LatencyRecorder();

        // Simulate 1 request taking 100ms when pace was 1 request per 10ms (10,000 micros)
        uncorrected.recordLatency(100_000L);
        corrected.recordLatencyWithExpectedInterval(100_000L, 10_000L);

        PercentileSnapshot uncorrSnapshot = uncorrected.createSnapshot(1.0);
        PercentileSnapshot corrSnapshot = corrected.createSnapshot(1.0);

        // Uncorrected has only 1 sample
        assertThat(uncorrSnapshot.totalRequests()).isEqualTo(1);

        // Corrected should have inserted compensating samples for the missing requests
        assertThat(corrSnapshot.totalRequests()).isGreaterThan(1);
        assertThat(corrSnapshot.p50Micros()).isLessThanOrEqualTo(corrSnapshot.p99Micros());
    }

    @Test
    @DisplayName("Should track status codes and error rates accurately")
    void testHttpStatusTracking() {
        LatencyRecorder recorder = new LatencyRecorder();

        recorder.recordStatus(200);
        recorder.recordStatus(201);
        recorder.recordStatus(404);
        recorder.recordStatus(500);
        recorder.recordTimeout();
        recorder.recordConnectionError();

        PercentileSnapshot snapshot = recorder.createSnapshot(2.0);

        assertThat(snapshot.status2xx()).isEqualTo(2);
        assertThat(snapshot.status4xx()).isEqualTo(1);
        assertThat(snapshot.status5xx()).isEqualTo(1);
        assertThat(snapshot.timeouts()).isEqualTo(1);
        assertThat(snapshot.connectionErrors()).isEqualTo(1);
        assertThat(snapshot.errorRate()).isEqualTo(4.0 / 6.0);
    }
}
