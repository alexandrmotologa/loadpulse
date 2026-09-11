package com.engine.loadpulse.cli;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import com.engine.loadpulse.report.DiffReportGenerator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(name = "diff", description = "Compare two benchmark JSON reports and check for performance regressions", mixinStandardHelpOptions = true)
public class DiffCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Baseline benchmark report (before)")
    private File beforeFile;

    @Parameters(index = "1", description = "Current benchmark report (after)")
    private File afterFile;

    @Option(names = {"--threshold"}, defaultValue = "10.0", description = "Regression failure threshold percentage for p99 latency (default: 10%%)")
    private double thresholdPct;

    @Option(names = {"--html"}, description = "Path to write standalone HTML diff report")
    private Path htmlReport;

    @Override
    public Integer call() throws Exception {
        if (!beforeFile.exists()) {
            System.err.println("Error: Baseline file does not exist: " + beforeFile.getAbsolutePath());
            return 1;
        }
        if (!afterFile.exists()) {
            System.err.println("Error: Current benchmark file does not exist: " + afterFile.getAbsolutePath());
            return 1;
        }

        PercentileSnapshot before = DiffReportGenerator.loadSnapshot(beforeFile);
        PercentileSnapshot after = DiffReportGenerator.loadSnapshot(afterFile);

        String consoleDiff = DiffReportGenerator.renderConsoleDiff(before, after, beforeFile.getName(), afterFile.getName());
        System.out.println(consoleDiff);

        if (htmlReport != null) {
            DiffReportGenerator.generateHtmlDiffReport(before, after, beforeFile.getName(), afterFile.getName(), htmlReport);
            System.out.println("HTML Diff report exported to: " + htmlReport.toAbsolutePath());
        }

        // Check p99 regression threshold
        double p99Before = before.p99Millis();
        double p99After = after.p99Millis();
        double p99DeltaPct = p99Before > 0 ? ((p99After - p99Before) / p99Before) * 100.0 : 0.0;

        if (p99DeltaPct > thresholdPct) {
            System.err.println(String.format("REGRESSION ALERT: p99 latency increased by +%.1f%%, which exceeds the %.1f%% threshold!",
                    p99DeltaPct, thresholdPct));
            return 1;
        }

        System.out.println("Performance diff passed: no critical regression detected.");
        return 0;
    }
}
