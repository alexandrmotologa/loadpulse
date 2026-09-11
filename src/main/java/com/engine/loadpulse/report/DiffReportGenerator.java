package com.engine.loadpulse.report;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class DiffReportGenerator {
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_GRAY = "\u001B[90m";

    public record MetricDiff(
            String metricName,
            double beforeVal,
            double afterVal,
            double pctChange,
            String formattedBefore,
            String formattedAfter,
            boolean isHigherBetter,
            boolean isRegression
    ) {
        public String statusBadge() {
            if (Math.abs(pctChange) < 1.0) {
                return "[NO CHANGE]";
            }
            if (isRegression) {
                return "[REGRESSION]";
            }
            return "[IMPROVED]";
        }
    }

    public static PercentileSnapshot loadSnapshot(File file) throws IOException {
        JsonNode root = MAPPER.readTree(file);
        JsonNode snapshotNode = root.has("snapshot") ? root.get("snapshot") : root;
        return MAPPER.treeToValue(snapshotNode, PercentileSnapshot.class);
    }

    public static String renderConsoleDiff(PercentileSnapshot before, PercentileSnapshot after, String beforeName, String afterName) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n").append(ANSI_BOLD).append(ANSI_CYAN)
                .append("============================ LOADPULSE REGRESSION DIFF ============================\n")
                .append(ANSI_RESET);

        sb.append(String.format("%-18s %-18s %-18s %-14s %-12s\n",
                "Metric", "Before (" + truncate(beforeName, 10) + ")", "After (" + truncate(afterName, 10) + ")", "Delta", "Status"));
        sb.append("------------------------------------------------------------------------------------\n");

        appendDiffRow(sb, "Throughput (RPS)", before.requestsPerSecond(), after.requestsPerSecond(),
                String.format("%,.0f req/s", before.requestsPerSecond()),
                String.format("%,.0f req/s", after.requestsPerSecond()), true);

        appendDiffRow(sb, "Latency p50", before.p50Millis(), after.p50Millis(),
                String.format("%.2f ms", before.p50Millis()),
                String.format("%.2f ms", after.p50Millis()), false);

        appendDiffRow(sb, "Latency p90", before.p90Millis(), after.p90Millis(),
                String.format("%.2f ms", before.p90Millis()),
                String.format("%.2f ms", after.p90Millis()), false);

        appendDiffRow(sb, "Latency p95", before.p95Millis(), after.p95Millis(),
                String.format("%.2f ms", before.p95Millis()),
                String.format("%.2f ms", after.p95Millis()), false);

        appendDiffRow(sb, "Latency p99", before.p99Millis(), after.p99Millis(),
                String.format("%.2f ms", before.p99Millis()),
                String.format("%.2f ms", after.p99Millis()), false);

        appendDiffRow(sb, "Latency Max", before.maxLatencyMillis(), after.maxLatencyMillis(),
                String.format("%.2f ms", before.maxLatencyMillis()),
                String.format("%.2f ms", after.maxLatencyMillis()), false);

        appendDiffRow(sb, "Error Rate", before.errorRate() * 100.0, after.errorRate() * 100.0,
                String.format("%.2f%%", before.errorRate() * 100.0),
                String.format("%.2f%%", after.errorRate() * 100.0), false);

        sb.append(ANSI_BOLD).append(ANSI_CYAN)
                .append("====================================================================================\n")
                .append(ANSI_RESET);

        return sb.toString();
    }

    private static void appendDiffRow(
            StringBuilder sb,
            String metric,
            double bVal,
            double aVal,
            String bFmt,
            String aFmt,
            boolean isHigherBetter
    ) {
        double pct = bVal > 0 ? ((aVal - bVal) / bVal) * 100.0 : (aVal > 0 ? 100.0 : 0.0);
        boolean isRegression = isHigherBetter ? (pct < -5.0) : (pct > 5.0);
        boolean isImproved = isHigherBetter ? (pct > 5.0) : (pct < -5.0);

        String color = isRegression ? ANSI_RED : (isImproved ? ANSI_GREEN : ANSI_GRAY);
        String status = isRegression ? "[REGRESSION]" : (isImproved ? "[IMPROVED]" : "[STABLE]");
        String deltaStr = String.format("%+6.1f%%", pct);

        sb.append(String.format("%-18s %-18s %-18s %s%-14s %-12s%s\n",
                metric, bFmt, aFmt, color, deltaStr, status, ANSI_RESET));
    }

    public static void generateHtmlDiffReport(
            PercentileSnapshot before,
            PercentileSnapshot after,
            String beforeLabel,
            String afterLabel,
            Path outputPath
    ) throws IOException {
        if (outputPath == null) return;

        double rpsPct = computePct(before.requestsPerSecond(), after.requestsPerSecond());
        double p50Pct = computePct(before.p50Millis(), after.p50Millis());
        double p95Pct = computePct(before.p95Millis(), after.p95Millis());
        double p99Pct = computePct(before.p99Millis(), after.p99Millis());

        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>LoadPulse Performance Diff</title>
                    <style>
                        :root {
                            --bg: #0d1117; --surface: #161b22; --border: #30363d;
                            --text: #c9d1d9; --text-muted: #8b949e; --green: #3fb950; --red: #f85149;
                        }
                        body { font-family: -apple-system, sans-serif; background: var(--bg); color: var(--text); padding: 32px; }
                        .container { max-width: 900px; margin: 0 auto; }
                        h1 { font-size: 24px; color: #fff; margin-bottom: 20px; }
                        table { width: 100%%; border-collapse: collapse; background: var(--surface); border: 1px solid var(--border); border-radius: 8px; }
                        th, td { padding: 12px 16px; border-bottom: 1px solid var(--border); text-align: left; font-size: 14px; }
                        th { color: var(--text-muted); font-size: 12px; text-transform: uppercase; }
                        .improved { color: var(--green); font-weight: bold; }
                        .regression { color: var(--red); font-weight: bold; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>LoadPulse Performance Regression Diff</h1>
                        <table>
                            <thead>
                                <tr><th>Metric</th><th>Before (%s)</th><th>After (%s)</th><th>Delta</th></tr>
                            </thead>
                            <tbody>
                                <tr><td>Throughput (RPS)</td><td>%,.0f req/s</td><td>%,.0f req/s</td><td class="%s">%+.1f%%</td></tr>
                                <tr><td>Latency p50</td><td>%.2f ms</td><td>%.2f ms</td><td class="%s">%+.1f%%</td></tr>
                                <tr><td>Latency p95</td><td>%.2f ms</td><td>%.2f ms</td><td class="%s">%+.1f%%</td></tr>
                                <tr><td>Latency p99</td><td>%.2f ms</td><td>%.2f ms</td><td class="%s">%+.1f%%</td></tr>
                            </tbody>
                        </table>
                    </div>
                </body>
                </html>
                """.formatted(
                beforeLabel, afterLabel,
                before.requestsPerSecond(), after.requestsPerSecond(), rpsPct >= 0 ? "improved" : "regression", rpsPct,
                before.p50Millis(), after.p50Millis(), p50Pct <= 0 ? "improved" : "regression", p50Pct,
                before.p95Millis(), after.p95Millis(), p95Pct <= 0 ? "improved" : "regression", p95Pct,
                before.p99Millis(), after.p99Millis(), p99Pct <= 0 ? "improved" : "regression", p99Pct
        );

        if (outputPath.toFile().getParentFile() != null) {
            outputPath.toFile().getParentFile().mkdirs();
        }
        Files.writeString(outputPath, html, StandardCharsets.UTF_8);
    }

    private static double computePct(double before, double after) {
        return before > 0 ? ((after - before) / before) * 100.0 : 0.0;
    }

    private static String truncate(String text, int max) {
        if (text == null) return "";
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
