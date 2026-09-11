package com.engine.loadpulse.cli;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import com.engine.loadpulse.domain.scenario.ScenarioDefinition;
import com.engine.loadpulse.engine.ScenarioExecutor;
import com.engine.loadpulse.report.HtmlReportGenerator;
import com.engine.loadpulse.report.JsonReportGenerator;
import com.engine.loadpulse.report.SlaAssertionEvaluator;
import com.engine.loadpulse.tui.TerminalHud;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "scenario", description = "Execute YAML scenario benchmark", mixinStandardHelpOptions = true)
public class ScenarioCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "YAML scenario file path")
    private File scenarioFile;

    @Option(names = {"--no-tui"}, description = "Disable ANSI live terminal HUD")
    private boolean noTui;

    @Option(names = {"--html"}, description = "Path to write standalone HTML benchmark report")
    private Path htmlReport;

    @Option(names = {"--json"}, description = "Path to write machine-readable JSON summary")
    private Path jsonReport;

    @Option(names = {"--assert"}, description = "SLA assertion override (e.g. 'p99 < 50ms && error_rate < 0.01')")
    private String assertionOverride;

    @Override
    public Integer call() throws Exception {
        if (!scenarioFile.exists()) {
            System.err.println("Error: Scenario file does not exist: " + scenarioFile.getAbsolutePath());
            return 1;
        }

        ScenarioDefinition scenario = ScenarioExecutor.loadFromFile(scenarioFile);
        String assertion = (assertionOverride != null && !assertionOverride.isBlank())
                ? assertionOverride
                : scenario.assertion();

        String targetDesc = scenario.name() != null ? scenario.name() : scenario.target();
        TerminalHud hud = new TerminalHud(targetDesc, scenario.concurrency(), scenario.parseDuration(), noTui);
        PercentileSnapshot finalSnapshot;

        try (hud; ScenarioExecutor executor = new ScenarioExecutor(scenario)) {
            finalSnapshot = executor.execute(hud::update);
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
            JsonReportGenerator.generateReport(finalSnapshot, targetDesc, scenario.concurrency(), jsonReport);
            System.out.println("JSON report exported to: " + jsonReport.toAbsolutePath());
        }

        if (htmlReport != null) {
            HtmlReportGenerator.generateReport(finalSnapshot, targetDesc, scenario.concurrency(), assertion, assertionResult, htmlReport);
            System.out.println("HTML report exported to: " + htmlReport.toAbsolutePath());
        }

        return (assertionResult == null || assertionResult.passed()) ? 0 : 1;
    }
}
