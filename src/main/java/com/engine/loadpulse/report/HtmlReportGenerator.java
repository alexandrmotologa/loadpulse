package com.engine.loadpulse.report;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class HtmlReportGenerator {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public static void generateReport(
            PercentileSnapshot snapshot,
            String target,
            int concurrency,
            String assertionExpression,
            SlaAssertionEvaluator.AssertionResult assertionResult,
            Path outputPath
    ) throws IOException {
        if (outputPath == null) {
            return;
        }

        String html = renderHtml(snapshot, target, concurrency, assertionExpression, assertionResult);

        File file = outputPath.toFile();
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        Files.writeString(outputPath, html, StandardCharsets.UTF_8);
    }

    private static String renderHtml(
            PercentileSnapshot s,
            String target,
            int concurrency,
            String assertionExpression,
            SlaAssertionEvaluator.AssertionResult assertionResult
    ) {
        String timestamp = TIME_FORMATTER.format(Instant.now());
        String slaBadge = "";
        if (assertionExpression != null && !assertionExpression.isBlank() && assertionResult != null) {
            if (assertionResult.passed()) {
                slaBadge = "<div class='badge badge-success'>SLA PASSED</div>";
            } else {
                slaBadge = "<div class='badge badge-danger'>SLA FAILED</div>";
            }
        }

        String distributionBars = renderDistributionSvgBars(s.histogramBuckets(), s.totalRequests());
        String percentileCurveSvg = renderPercentileCurveSvg(s);

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>LoadPulse Report — %s</title>
                    <style>
                        :root {
                            --bg: #0d1117;
                            --surface: #161b22;
                            --border: #30363d;
                            --text: #c9d1d9;
                            --text-muted: #8b949e;
                            --accent: #58a6ff;
                            --accent-hover: #79c0ff;
                            --green: #3fb950;
                            --yellow: #d29922;
                            --red: #f85149;
                            --purple: #bc8cff;
                        }
                        * { box-sizing: border-box; margin: 0; padding: 0; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                            background-color: var(--bg);
                            color: var(--text);
                            padding: 32px 20px;
                            line-height: 1.5;
                        }
                        .container { max-width: 1100px; margin: 0 auto; }
                        .header {
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            margin-bottom: 24px;
                            border-bottom: 1px solid var(--border);
                            padding-bottom: 16px;
                        }
                        .header h1 { font-size: 24px; font-weight: 700; color: #fff; }
                        .header .meta { font-size: 13px; color: var(--text-muted); }
                        .badge {
                            display: inline-block;
                            padding: 4px 10px;
                            border-radius: 6px;
                            font-size: 12px;
                            font-weight: 600;
                            letter-spacing: 0.5px;
                        }
                        .badge-success { background: rgba(63, 185, 80, 0.2); color: var(--green); border: 1px solid var(--green); }
                        .badge-danger { background: rgba(248, 81, 73, 0.2); color: var(--red); border: 1px solid var(--red); }
                        .grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
                            gap: 16px;
                            margin-bottom: 24px;
                        }
                        .card {
                            background: var(--surface);
                            border: 1px solid var(--border);
                            border-radius: 8px;
                            padding: 20px;
                        }
                        .card-title { font-size: 13px; color: var(--text-muted); text-transform: uppercase; margin-bottom: 8px; font-weight: 600; }
                        .card-value { font-size: 28px; font-weight: 700; color: #fff; }
                        .card-sub { font-size: 12px; color: var(--text-muted); margin-top: 4px; }
                        .section-title { font-size: 18px; font-weight: 600; margin: 24px 0 12px; color: #fff; }
                        .chart-container {
                            background: var(--surface);
                            border: 1px solid var(--border);
                            border-radius: 8px;
                            padding: 24px;
                            margin-bottom: 24px;
                        }
                        table {
                            width: 100%%;
                            border-collapse: collapse;
                            margin-top: 8px;
                        }
                        th, td {
                            padding: 10px 14px;
                            text-align: left;
                            border-bottom: 1px solid var(--border);
                            font-size: 14px;
                        }
                        th { color: var(--text-muted); font-weight: 600; font-size: 12px; text-transform: uppercase; }
                        tr:last-child td { border-bottom: none; }
                        .text-green { color: var(--green); }
                        .text-yellow { color: var(--yellow); }
                        .text-red { color: var(--red); }
                        .footer {
                            margin-top: 40px;
                            text-align: center;
                            font-size: 12px;
                            color: var(--text-muted);
                            border-top: 1px solid var(--border);
                            padding-top: 20px;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <div>
                                <h1>LoadPulse Benchmark Report</h1>
                                <div class="meta">Target: <strong>%s</strong> &bull; Generated: %s</div>
                            </div>
                            <div>%s</div>
                        </div>

                        <div class="grid">
                            <div class="card">
                                <div class="card-title">Throughput</div>
                                <div class="card-value">%,.0f <span style="font-size: 16px; font-weight: normal; color: var(--text-muted)">req/s</span></div>
                                <div class="card-sub">Total %,d requests in %.1fs</div>
                            </div>
                            <div class="card">
                                <div class="card-title">p99 Latency</div>
                                <div class="card-value">%.2f <span style="font-size: 16px; font-weight: normal; color: var(--text-muted)">ms</span></div>
                                <div class="card-sub">p50: %.2fms &bull; p90: %.2fms</div>
                            </div>
                            <div class="card">
                                <div class="card-title">Success Rate</div>
                                <div class="card-value text-green">%.2f%%</div>
                                <div class="card-sub">2xx: %,d &bull; Errors: %,d</div>
                            </div>
                            <div class="card">
                                <div class="card-title">Bandwidth</div>
                                <div class="card-value">%.2f <span style="font-size: 16px; font-weight: normal; color: var(--text-muted)">Mbps</span></div>
                                <div class="card-sub">%d Virtual Thread workers</div>
                            </div>
                        </div>

                        <div class="section-title">Percentile Ladder Curve</div>
                        <div class="chart-container">
                            %s
                        </div>

                        <div class="section-title">Latency Distribution</div>
                        <div class="chart-container">
                            %s
                        </div>

                        <div class="section-title">Percentile Breakdown</div>
                        <div class="card">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Percentile</th>
                                        <th>Latency (ms)</th>
                                        <th>Latency (µs)</th>
                                        <th>Description</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr><td>Min</td><td>%.3f ms</td><td>%,d µs</td><td>Fastest recorded request</td></tr>
                                    <tr><td>p50</td><td>%.3f ms</td><td>%,d µs</td><td>Median latency (50%% of requests)</td></tr>
                                    <tr><td>p75</td><td>%.3f ms</td><td>%,d µs</td><td>75th percentile</td></tr>
                                    <tr><td>p90</td><td>%.3f ms</td><td>%,d µs</td><td>90th percentile</td></tr>
                                    <tr><td>p95</td><td>%.3f ms</td><td>%,d µs</td><td>95th percentile</td></tr>
                                    <tr><td>p99</td><td>%.3f ms</td><td>%,d µs</td><td>99th percentile (High latency threshold)</td></tr>
                                    <tr><td>p99.9</td><td>%.3f ms</td><td>%,d µs</td><td>99.9th percentile (Three-nines SLA)</td></tr>
                                    <tr><td>p99.99</td><td>%.3f ms</td><td>%,d µs</td><td>99.99th percentile (Four-nines SLA)</td></tr>
                                    <tr><td>Max</td><td>%.3f ms</td><td>%,d µs</td><td>Worst recorded latency spike</td></tr>
                                </tbody>
                            </table>
                        </div>

                        <div class="section-title">HTTP Status Codes & Errors</div>
                        <div class="card">
                            <table>
                                <thead>
                                    <tr>
                                        <th>Status Category</th>
                                        <th>Count</th>
                                        <th>Share</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    <tr><td>2xx Success</td><td class="text-green">%,d</td><td>%.2f%%</td></tr>
                                    <tr><td>3xx Redirection</td><td>%,d</td><td>%.2f%%</td></tr>
                                    <tr><td>4xx Client Errors</td><td class="text-yellow">%,d</td><td>%.2f%%</td></tr>
                                    <tr><td>5xx Server Errors</td><td class="text-red">%,d</td><td>%.2f%%</td></tr>
                                    <tr><td>Timeouts</td><td class="text-red">%,d</td><td>-</td></tr>
                                    <tr><td>Connection Errors</td><td class="text-red">%,d</td><td>-</td></tr>
                                </tbody>
                            </table>
                        </div>

                        <div class="footer">
                            LoadPulse &bull; Reactive HTTP & gRPC Load Generator &bull; Built with Java 21 Virtual Threads & HdrHistogram
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(
                target,
                target,
                timestamp,
                slaBadge,
                s.requestsPerSecond(),
                s.totalRequests(),
                s.durationSeconds(),
                s.p99Millis(),
                s.p50Millis(),
                s.p90Millis(),
                (1.0 - s.errorRate()) * 100.0,
                s.status2xx(),
                s.status4xx() + s.status5xx() + s.timeouts() + s.connectionErrors(),
                s.throughputMbps(),
                concurrency,
                percentileCurveSvg,
                distributionBars,
                s.minLatencyMillis(), s.minLatencyMicros(),
                s.p50Millis(), s.p50Micros(),
                s.p75Millis(), s.p75Micros(),
                s.p90Millis(), s.p90Micros(),
                s.p95Millis(), s.p95Micros(),
                s.p99Millis(), s.p99Micros(),
                s.p999Millis(), s.p999Micros(),
                s.p9999Millis(), s.p9999Micros(),
                s.maxLatencyMillis(), s.maxLatencyMicros(),
                s.status2xx(), s.totalRequests() > 0 ? (s.status2xx() * 100.0 / s.totalRequests()) : 0.0,
                s.status3xx(), s.totalRequests() > 0 ? (s.status3xx() * 100.0 / s.totalRequests()) : 0.0,
                s.status4xx(), s.totalRequests() > 0 ? (s.status4xx() * 100.0 / s.totalRequests()) : 0.0,
                s.status5xx(), s.totalRequests() > 0 ? (s.status5xx() * 100.0 / s.totalRequests()) : 0.0,
                s.timeouts(),
                s.connectionErrors()
        );
    }

    private static String renderDistributionSvgBars(Map<String, Long> buckets, long total) {
        if (buckets == null || buckets.isEmpty() || total <= 0) {
            return "<div style='color: var(--text-muted); padding: 20px 0;'>No latency samples recorded</div>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div style='display: flex; flex-direction: column; gap: 10px;'>");

        long maxCount = 1;
        for (long c : buckets.values()) {
            if (c > maxCount) maxCount = c;
        }

        for (Map.Entry<String, Long> entry : buckets.entrySet()) {
            String label = entry.getKey();
            long count = entry.getValue();
            double pct = (double) count / total * 100.0;
            double widthPct = ((double) count / maxCount) * 100.0;

            sb.append("<div style='display: flex; align-items: center; font-size: 13px;'>");
            sb.append(String.format("<span style='width: 90px; color: var(--text-muted);'>%s</span>", label));
            sb.append("<div style='flex: 1; background: #21262d; border-radius: 4px; height: 18px; margin: 0 12px; overflow: hidden;'>");
            sb.append(String.format("<div style='background: #58a6ff; height: 100%%; width: %.2f%%; border-radius: 4px;'></div>", Math.max(0.5, widthPct)));
            sb.append("</div>");
            sb.append(String.format("<span style='width: 120px; text-align: right;'>%,d (%.1f%%)</span>", count, pct));
            sb.append("</div>");
        }

        sb.append("</div>");
        return sb.toString();
    }

    private static String renderPercentileCurveSvg(PercentileSnapshot s) {
        double[] percentiles = {0.0, 50.0, 75.0, 90.0, 95.0, 99.0, 99.9, 99.99, 100.0};
        double[] values = {
                s.minLatencyMillis(),
                s.p50Millis(),
                s.p75Millis(),
                s.p90Millis(),
                s.p95Millis(),
                s.p99Millis(),
                s.p999Millis(),
                s.p9999Millis(),
                s.maxLatencyMillis()
        };

        double maxVal = Math.max(s.maxLatencyMillis(), 1.0);
        int width = 800;
        int height = 240;
        int padding = 40;

        StringBuilder pathData = new StringBuilder();
        StringBuilder points = new StringBuilder();

        for (int i = 0; i < percentiles.length; i++) {
            double x = padding + (i * (width - 2 * padding) / (double) (percentiles.length - 1));
            double y = (height - padding) - ((values[i] / maxVal) * (height - 2 * padding));

            if (i == 0) {
                pathData.append(String.format("M %.1f,%.1f", x, y));
            } else {
                pathData.append(String.format(" L %.1f,%.1f", x, y));
            }

            points.append(String.format("<circle cx='%.1f' cy='%.1f' r='4' fill='#58a6ff'/>", x, y));
            double labelY = (double) (height - padding + 16);
            String pLabel = i == 0 ? "0" : (i == percentiles.length - 1 ? "max" : String.valueOf(percentiles[i]));
            points.append(String.format("<text x='%.1f' y='%.1f' fill='#8b949e' font-size='10' text-anchor='middle'>p%s</text>",
                    x, labelY, pLabel));
        }

        return String.format("""
                <svg viewBox="0 0 %d %d" style="width: 100%%; height: auto; overflow: visible;">
                    <line x1="%d" y1="%d" x2="%d" y2="%d" stroke="#30363d" stroke-dasharray="4"/>
                    <line x1="%d" y1="%d" x2="%d" y2="%d" stroke="#30363d"/>
                    <path d="%s" fill="none" stroke="#58a6ff" stroke-width="2.5"/>
                    %s
                    <text x="%d" y="20" fill="#8b949e" font-size="11">Max: %.2f ms</text>
                </svg>
                """,
                width, height,
                padding, 20, width - padding, 20,
                padding, height - padding, width - padding, height - padding,
                pathData,
                points,
                padding, maxVal
        );
    }
}
