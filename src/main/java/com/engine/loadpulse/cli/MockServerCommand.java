package com.engine.loadpulse.cli;

import com.engine.loadpulse.domain.scenario.ScenarioDefinition;
import com.engine.loadpulse.mock.MockBenchmarkServer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.Duration;
import java.util.concurrent.Callable;

@Command(name = "mock", description = "Start in-memory HTTP mock benchmark server for local testing", mixinStandardHelpOptions = true)
public class MockServerCommand implements Callable<Integer> {

    @Option(names = {"-p", "--port"}, defaultValue = "8080", description = "Port to listen on (0 for random)")
    private int port;

    @Option(names = {"--delay"}, defaultValue = "0ms", description = "Simulated response delay (e.g. 5ms, 20ms)")
    private String delay;

    @Option(names = {"--jitter"}, defaultValue = "0ms", description = "Simulated delay jitter (e.g. 2ms)")
    private String jitter;

    @Option(names = {"--error-rate"}, defaultValue = "0.0", description = "Error injection rate (0.0 to 1.0, e.g. 0.05 for 5%)")
    private double errorRate;

    @Override
    public Integer call() throws Exception {
        Duration delayDuration = ScenarioDefinition.parseTimeDuration(delay, Duration.ZERO);
        Duration jitterDuration = ScenarioDefinition.parseTimeDuration(jitter, Duration.ZERO);

        try (MockBenchmarkServer server = new MockBenchmarkServer(port, delayDuration, jitterDuration, errorRate)) {
            server.start();

            System.out.println("LoadPulse In-Memory Mock Server running!");
            System.out.println("  • URL:        " + server.getBaseUrl());
            System.out.println("  • Delay:      " + delayDuration.toMillis() + "ms (Jitter: ±" + jitterDuration.toMillis() + "ms)");
            System.out.println("  • Error Rate: " + String.format("%.1f%%", errorRate * 100.0));
            System.out.println("\nPress Ctrl+C to terminate.");

            // Keep alive
            Thread.currentThread().join();
        }

        return 0;
    }
}
