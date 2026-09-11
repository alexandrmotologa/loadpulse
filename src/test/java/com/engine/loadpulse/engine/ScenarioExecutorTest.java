package com.engine.loadpulse.engine;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import com.engine.loadpulse.domain.scenario.ScenarioDefinition;
import com.engine.loadpulse.mock.MockBenchmarkServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ScenarioExecutorTest {

    @Test
    @DisplayName("Should execute multi-step YAML scenario with dynamic interpolation")
    void testScenarioExecution() throws Exception {
        try (MockBenchmarkServer mockServer = MockBenchmarkServer.startOnRandomPort(Duration.ofMillis(5), Duration.ZERO)) {
            String yaml = """
                    name: "Checkout flow test"
                    target: "%s"
                    concurrency: 10
                    duration: "1s"
                    warmup: "0s"
                    steps:
                      - name: "Health check"
                        path: "/health"
                        method: "GET"
                      - name: "Create item"
                        path: "/"
                        method: "POST"
                        headers:
                          Content-Type: "application/json"
                          X-Trace-Id: "{{ uuid() }}"
                        body: '{"id": "{{ uuid() }}", "amount": {{ random_int(10, 50) }}}'
                    """.formatted(mockServer.getBaseUrl());

            ScenarioDefinition scenario = ScenarioExecutor.loadFromString(yaml);
            assertThat(scenario.name()).isEqualTo("Checkout flow test");
            assertThat(scenario.steps()).hasSize(2);

            try (ScenarioExecutor executor = new ScenarioExecutor(scenario)) {
                PercentileSnapshot snapshot = executor.execute(null);

                assertThat(snapshot.totalRequests()).isGreaterThan(10);
                assertThat(snapshot.status2xx()).isGreaterThan(10);
                assertThat(snapshot.status5xx()).isEqualTo(0);
                assertThat(snapshot.errorRate()).isEqualTo(0.0);
            }
        }
    }
}
