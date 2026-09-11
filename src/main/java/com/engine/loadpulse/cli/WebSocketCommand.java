package com.engine.loadpulse.cli;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import com.engine.loadpulse.domain.scenario.ScenarioDefinition;
import com.engine.loadpulse.engine.websocket.WebSocketBenchmarkEngine;
import com.engine.loadpulse.report.HtmlReportGenerator;
import com.engine.loadpulse.report.JsonReportGenerator;
import com.engine.loadpulse.report.SlaAssertionEvaluator;
import com.engine.loadpulse.tui.TerminalHud;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;

@Command(name = "ws", description = "Benchmark WebSocket connections and message round-trip latency", mixinStandardHelpOptions = true)
public class WebSocketCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Target WebSocket URL (e.g. ws://localhost:8080/ws)")
    private URI targetUri;

    @Option(names = {"-c", "--concurrency"}, defaultValue = "50", description = "Number of concurrent WebSocket connections")
    private int concurrency;

    @Option(names = {"-d", "--duration"}, defaultValue = "10s", description = "Benchmark duration (e.g. 10s, 30s)")
    private String duration;

    @Option(names = {"--msg"}, defaultValue = "ping", description = "Text message to transmit over each WebSocket")
    private String message;

    @Option(names = {"--interval"}, defaultValue = "500ms", description = "Interval between message frames per worker")
    private String interval;

    @Option(names = {"--no-tui"}, description = "Disable ANSI live terminal HUD")
    private boolean noTui;

    @Option(names = {"--html"}, description = "Path to write standalone HTML report")
    private Path htmlReport;

    @Option(names = {"--json"}, description = "Path to write machine-readable JSON summary")
    private Path jsonReport;

    @Option(names = {"--assert"}, description = "SLA assertion threshold (e.g. 'p99 < 50ms && error_rate < 0.01')")
    private String assertion;

    @Override
    public Integer call() throws Exception {
        Duration benchDuration = ScenarioDefinition.parseTimeDuration(duration, Duration.ofSeconds(10));
        Duration sendInterval = ScenarioDefinition.parseTimeDuration(interval, Duration.ofMillis(500));

        TerminalHud hud = new TerminalHud("WebSocket: " + targetUri.toString(), concurrency, benchDuration, noTui);
        PercentileSnapshot finalSnapshot;

        try (hud; WebSocketBenchmarkEngine engine = new WebSocketBenchmarkEngine(targetUri, concurrency, benchDuration, message, sendInterval)) {
            finalSnapshot = engine.runBenchmark(hud::update);
        }

        hud.renderFinalSummary(finalSnapshot);

        SlaAssertionEvaluator.AssertionResult assertionResult = null;
        if (assertion != null && !assertion.isBlank()) {
            assertionResult = SlaAssertionEvaluator.evaluate(assertion, finalSnapshot);
            System.out.println("\nSLA Verification:");
            for (String detail : assertionResult.details()) {
                System.out.println("  • " + detail);
            }
            if (!assertionResult.passed()) {
                System.err.println("\nERROR: " + assertionResult.summary());
            } else {
                System.out.println("\nSUCCESS: " + assertionResult.summary());
            }
        }

        if (jsonReport != null) {
            JsonReportGenerator.generateReport(finalSnapshot, targetUri.toString(), concurrency, jsonReport);
            System.out.println("JSON report exported to: " + jsonReport.toAbsolutePath());
        }

        if (htmlReport != null) {
            HtmlReportGenerator.generateReport(finalSnapshot, targetUri.toString(), concurrency, assertion, assertionResult, htmlReport);
            System.out.println("HTML report exported to: " + htmlReport.toAbsolutePath());
        }

        return (assertionResult == null || assertionResult.passed()) ? 0 : 1;
    }
}
