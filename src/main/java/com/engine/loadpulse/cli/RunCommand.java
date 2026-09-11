package com.engine.loadpulse.cli;

import com.engine.loadpulse.domain.model.BenchmarkConfig;
import com.engine.loadpulse.domain.model.HttpMethod;
import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import com.engine.loadpulse.domain.scenario.ScenarioDefinition;
import com.engine.loadpulse.engine.WorkerPool;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "run", description = "Execute reactive benchmark against a target endpoint", mixinStandardHelpOptions = true)
public class RunCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Target HTTP URL to benchmark")
    private URI targetUri;

    @Option(names = {"-c", "--concurrency"}, defaultValue = "50", description = "Number of concurrent Virtual Thread workers")
    private int concurrency;

    @Option(names = {"-d", "--duration"}, defaultValue = "10s", description = "Benchmark duration (e.g. 10s, 1m)")
    private String duration;

    @Option(names = {"--warmup"}, defaultValue = "0s", description = "Warm-up duration before recording metrics (e.g. 3s)")
    private String warmup;

    @Option(names = {"-m", "--method"}, defaultValue = "GET", description = "HTTP method (GET, POST, PUT, DELETE, PATCH, HEAD)")
    private String method;

    @Option(names = {"-r", "--rps"}, defaultValue = "0", description = "Target requests per second (rate pacing; 0 = closed concurrency)")
    private int rps;

    @Option(names = {"-H", "--header"}, description = "Custom header in 'Key: Value' format (can be repeated)")
    private List<String> headers;

    @Option(names = {"-b", "--body"}, description = "Request body payload (supports dynamic templates like {{ uuid() }})")
    private String body;

    @Option(names = {"--timeout"}, defaultValue = "10s", description = "Per-request timeout (e.g. 5s)")
    private String timeout;

    @Option(names = {"--http1"}, description = "Force HTTP/1.1 instead of default HTTP/2")
    private boolean http1;

    @Option(names = {"--no-tui"}, description = "Disable ANSI live terminal HUD (auto-detected in CI)")
    private boolean noTui;

    @Option(names = {"--html"}, description = "Path to write standalone HTML benchmark report")
    private Path htmlReport;

    @Option(names = {"--json"}, description = "Path to write machine-readable JSON summary")
    private Path jsonReport;

    @Option(names = {"--assert"}, description = "SLA assertion threshold (e.g. 'p99 < 50ms && error_rate < 0.01')")
    private String assertion;

    @Override
    public Integer call() throws Exception {
        Map<String, String> headerMap = new HashMap<>();
        if (headers != null) {
            for (String h : headers) {
                int idx = h.indexOf(':');
                if (idx > 0) {
                    headerMap.put(h.substring(0, idx).trim(), h.substring(idx + 1).trim());
                }
            }
        }

        Duration benchDuration = ScenarioDefinition.parseTimeDuration(duration, Duration.ofSeconds(10));
        Duration warmupDuration = ScenarioDefinition.parseTimeDuration(warmup, Duration.ZERO);
        Duration reqTimeout = ScenarioDefinition.parseTimeDuration(timeout, Duration.ofSeconds(10));

        BenchmarkConfig config = BenchmarkConfig.builder()
                .targetUri(targetUri)
                .method(HttpMethod.fromString(method))
                .concurrency(concurrency)
                .duration(benchDuration)
                .warmupDuration(warmupDuration)
                .targetRps(rps)
                .headers(headerMap)
                .body(body)
                .requestTimeout(reqTimeout)
                .http2(!http1)
                .noTui(noTui)
                .jsonReportPath(jsonReport)
                .htmlReportPath(htmlReport)
                .assertionExpression(assertion)
                .build();

        TerminalHud hud = new TerminalHud(targetUri.toString(), concurrency, benchDuration, noTui);
        PercentileSnapshot finalSnapshot;

        try (hud; WorkerPool workerPool = new WorkerPool(config)) {
            finalSnapshot = workerPool.runBenchmark(hud::update);
        }

        hud.renderFinalSummary(finalSnapshot);

        // Evaluate SLA assertions if specified
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

        // Export reports
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
