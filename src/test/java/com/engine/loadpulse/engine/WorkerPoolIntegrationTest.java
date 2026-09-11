package com.engine.loadpulse.engine;

import com.engine.loadpulse.domain.model.BenchmarkConfig;
import com.engine.loadpulse.domain.model.HttpMethod;
import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import com.engine.loadpulse.mock.MockBenchmarkServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerPoolIntegrationTest {

    @Test
    @DisplayName("Should execute high-concurrency benchmark against mock server and record accurate latencies")
    void testWorkerPoolBenchmarkAgainstMockServer() throws Exception {
        Duration mockDelay = Duration.ofMillis(10);
        Duration mockJitter = Duration.ofMillis(2);

        try (MockBenchmarkServer mockServer = MockBenchmarkServer.startOnRandomPort(mockDelay, mockJitter)) {
            BenchmarkConfig config = BenchmarkConfig.builder()
                    .targetUri(URI.create(mockServer.getBaseUrl() + "/test"))
                    .method(HttpMethod.GET)
                    .concurrency(30)
                    .duration(Duration.ofSeconds(2))
                    .warmupDuration(Duration.ofMillis(500))
                    .requestTimeout(Duration.ofSeconds(5))
                    .http2(false)
                    .build();

            try (WorkerPool workerPool = new WorkerPool(config)) {
                PercentileSnapshot snapshot = workerPool.runBenchmark(null);

                assertThat(snapshot.totalRequests()).isGreaterThan(100);
                assertThat(snapshot.status2xx()).isGreaterThan(100);
                assertThat(snapshot.status5xx()).isEqualTo(0);
                assertThat(snapshot.errorRate()).isEqualTo(0.0);

                // p50 should be >= 8ms (delay was 10ms +/- 2ms)
                assertThat(snapshot.p50Millis()).isGreaterThanOrEqualTo(8.0);
                assertThat(snapshot.p99Millis()).isGreaterThanOrEqualTo(snapshot.p50Millis());
                assertThat(snapshot.requestsPerSecond()).isGreaterThan(50.0);
            }
        }
    }
}
