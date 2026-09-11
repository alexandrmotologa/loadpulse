package com.engine.loadpulse.report;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SlaAssertionEvaluatorTest {

    private PercentileSnapshot createDummySnapshot(long p99Micros, double errorRate, double rps) {
        return new PercentileSnapshot(
                1000L,
                10.0,
                rps,
                50000.0,
                1000L,
                p99Micros * 2,
                p99Micros / 2.0,
                100.0,
                p99Micros / 3,
                p99Micros / 2,
                (long) (p99Micros * 0.8),
                (long) (p99Micros * 0.9),
                p99Micros,
                p99Micros + 1000,
                p99Micros + 2000,
                (long) (1000 * (1.0 - errorRate)),
                0L,
                0L,
                (long) (1000 * errorRate),
                0L,
                0L,
                errorRate,
                Collections.emptyMap(),
                Map.of("0-1ms", 1000L)
        );
    }

    @Test
    @DisplayName("Should pass when all SLA thresholds are satisfied")
    void testSuccessfulSlaAssertion() {
        // p99 = 25ms (25,000 µs), error_rate = 0.01 (1%), rps = 500
        PercentileSnapshot snapshot = createDummySnapshot(25_000L, 0.01, 500.0);

        String assertion = "p99 < 50ms && error_rate < 0.05 && rps >= 400";
        SlaAssertionEvaluator.AssertionResult result = SlaAssertionEvaluator.evaluate(assertion, snapshot);

        assertThat(result.passed()).isTrue();
        assertThat(result.failures()).isEmpty();
        assertThat(result.details()).hasSize(3);
    }

    @Test
    @DisplayName("Should fail and report violations when SLA thresholds are broken")
    void testFailedSlaAssertion() {
        // p99 = 80ms (80,000 µs), error_rate = 0.08 (8%)
        PercentileSnapshot snapshot = createDummySnapshot(80_000L, 0.08, 100.0);

        String assertion = "p99 < 50ms && error_rate < 0.02";
        SlaAssertionEvaluator.AssertionResult result = SlaAssertionEvaluator.evaluate(assertion, snapshot);

        assertThat(result.passed()).isFalse();
        assertThat(result.failures()).hasSize(2);
        assertThat(result.failures().get(0)).contains("p99 < 50ms");
        assertThat(result.failures().get(1)).contains("error_rate < 0.02");
    }
}
