package com.engine.loadpulse.cli;

import com.engine.loadpulse.cli.curl.CurlParser;
import com.engine.loadpulse.domain.model.BenchmarkConfig;
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

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;

@Command(name = "from-curl", description = "Execute benchmark by directly importing a cURL command", mixinStandardHelpOptions = true)
public class FromCurlCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Raw cURL command string (e.g. \"curl 'http://api...' -X POST -H '...' -d '...'\")")
    private String curlCommand;

    @Option(names = {"-c", "--concurrency"}, defaultValue = "50", description = "Number of concurrent Virtual Thread workers")
    private int concurrency;

    @Option(names = {"-d", "--duration"}, defaultValue = "10s", description = "Benchmark duration (e.g. 10s, 1m)")
    private String duration;

    @Option(names = {"--warmup"}, defaultValue = "0s", description = "Warm-up duration before recording metrics (e.g. 3s)")
    private String warmup;

    @Option(names = {"--ramp-up"}, defaultValue = "0s", description = "Ramp-up duration from 1 to target concurrency (e.g. 10s)")
    private String rampUp;

    @Option(names = {"-r", "--rps"}, defaultValue = "0", description = "Target requests per second (rate pacing; 0 = closed concurrency)")
    private int rps;

    @Option(names = {"--timeout"}, defaultValue = "10s", description = "Per-request timeout (e.g. 5s)")
    private String timeout;

    @Option(names = {"--http1"}, description = "Force HTTP/1.1 instead of default HTTP/2")
    private boolean http1;

    @Option(names = {"--no-tui"}, description = "Disable ANSI live terminal HUD")
    private boolean noTui;

    @Option(names = {"--html"}, description = "Path to write standalone HTML benchmark report")
    private Path htmlReport;

    @Option(names = {"--json"}, description = "Path to write machine-readable JSON summary")
    private Path jsonReport;

    @Option(names = {"--assert"}, description = "SLA assertion threshold (e.g. 'p99 < 50ms && error_rate < 0.01')")
    private String assertion;

    @Override
    public Integer call() throws Exception {
        CurlParser.ParsedCurl parsed = CurlParser.parse(curlCommand);

        Duration benchDuration = ScenarioDefinition.parseTimeDuration(duration, Duration.ofSeconds(10));
        Duration warmupDuration = ScenarioDefinition.parseTimeDuration(warmup, Duration.ZERO);
        Duration rampUpDuration = ScenarioDefinition.parseTimeDuration(rampUp, Duration.ZERO);
        Duration reqTimeout = ScenarioDefinition.parseTimeDuration(timeout, Duration.ofSeconds(10));

        BenchmarkConfig config = BenchmarkConfig.builder()
                .targetUri(parsed.targetUri())
                .method(parsed.method())
                .concurrency(concurrency)
                .duration(benchDuration)
                .warmupDuration(warmupDuration)
                .rampUpDuration(rampUpDuration)
                .targetRps(rps)
                .headers(parsed.headers())
                .body(parsed.body())
                .requestTimeout(reqTimeout)
                .http2(!http1)
                .noTui(noTui)
                .jsonReportPath(jsonReport)
                .htmlReportPath(htmlReport)
                .assertionExpression(assertion)
                .build();

        TerminalHud hud = new TerminalHud(parsed.targetUri().toString(), concurrency, benchDuration, noTui);
        PercentileSnapshot finalSnapshot;

        try (hud;
             WorkerPool workerPool = new WorkerPool(config);
             com.engine.loadpulse.tui.TerminalKeyboardListener keyListener = new com.engine.loadpulse.tui.TerminalKeyboardListener(workerPool)) {
            keyListener.start();
            finalSnapshot = workerPool.runBenchmark(hud::update);
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
            JsonReportGenerator.generateReport(finalSnapshot, parsed.targetUri().toString(), concurrency, jsonReport);
            System.out.println("JSON report exported to: " + jsonReport.toAbsolutePath());
        }

        if (htmlReport != null) {
            HtmlReportGenerator.generateReport(finalSnapshot, parsed.targetUri().toString(), concurrency, assertion, assertionResult, htmlReport);
            System.out.println("HTML report exported to: " + htmlReport.toAbsolutePath());
        }

        return (assertionResult == null || assertionResult.passed()) ? 0 : 1;
    }
}
