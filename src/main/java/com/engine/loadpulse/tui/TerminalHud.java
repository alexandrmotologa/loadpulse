package com.engine.loadpulse.tui;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;

import java.io.PrintStream;
import java.time.Duration;

public class TerminalHud implements AutoCloseable {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_GRAY = "\u001B[90m";
    private static final String ANSI_CLEAR_SCREEN = "\u001B[H\u001B[2J";
    private static final String ANSI_HIDE_CURSOR = "\u001B[?25l";
    private static final String ANSI_SHOW_CURSOR = "\u001B[?25h";

    private final PrintStream out;
    private final String targetDescription;
    private final int concurrency;
    private final Duration totalDuration;
    private final boolean interactive;

    private double peakRps = 0.0;
    private long lastLogSecond = -1;

    public TerminalHud(String targetDescription, int concurrency, Duration totalDuration, boolean forceNoTui) {
        this.out = System.out;
        this.targetDescription = targetDescription;
        this.concurrency = concurrency;
        this.totalDuration = totalDuration;

        // Interactive if not disabled, console is available, and not inside standard CI
        boolean isConsole = System.console() != null;
        boolean isCi = System.getenv("CI") != null || System.getenv("GITHUB_ACTIONS") != null;
        this.interactive = !forceNoTui && isConsole && !isCi;

        if (this.interactive) {
            out.print(ANSI_HIDE_CURSOR);
        }
    }

    public synchronized void update(PercentileSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        if (snapshot.requestsPerSecond() > peakRps) {
            peakRps = snapshot.requestsPerSecond();
        }

        if (interactive) {
            renderInteractiveDashboard(snapshot);
        } else {
            renderHeadlessLine(snapshot);
        }
    }

    private void renderInteractiveDashboard(PercentileSnapshot s) {
        StringBuilder sb = new StringBuilder(ANSI_CLEAR_SCREEN);

        long elapsedSec = Math.round(s.durationSeconds());
        long totalSec = totalDuration.toSeconds();
        long remainingSec = Math.max(0, totalSec - elapsedSec);

        // Header
        sb.append(ANSI_BOLD).append(ANSI_CYAN).append("LOADPULSE").append(ANSI_RESET)
                .append(ANSI_GRAY).append(" ─ High-Throughput Reactive Benchmark Engine\n").append(ANSI_RESET);
        sb.append(ANSI_GRAY).append("Target: ").append(ANSI_RESET).append(targetDescription)
                .append(ANSI_GRAY).append(" │ Workers: ").append(ANSI_RESET).append(concurrency).append(" (Virtual Threads)")
                .append(ANSI_GRAY).append(" │ Time: ").append(ANSI_RESET)
                .append(String.format("%02d:%02d / %02d:%02d", elapsedSec / 60, elapsedSec % 60, totalSec / 60, totalSec % 60))
                .append(ANSI_GRAY).append(" (").append(remainingSec).append("s left)\n").append(ANSI_RESET);

        sb.append(ANSI_GRAY).append("──────────────────────────────────────────────────────────────────────────────\n").append(ANSI_RESET);

        // Throughput & Speedometer
        sb.append(ANSI_BOLD).append("Throughput:\n").append(ANSI_RESET);
        sb.append("  ").append(SpeedometerWidget.render(s.requestsPerSecond(), peakRps, s.throughputBytesPerSecond())).append("\n\n");

        // Latency Matrix
        sb.append(ANSI_BOLD).append("Latency Percentiles (Microsecond Precision):\n").append(ANSI_RESET);
        sb.append(ANSI_GRAY).append("  Min        p50        p75        p90        p95        p99        p99.9      Max\n").append(ANSI_RESET);
        sb.append(String.format("  %-10s %-10s %-10s %-10s %-10s %-10s %-10s %-10s\n\n",
                formatMs(s.minLatencyMillis()),
                formatMs(s.p50Millis()),
                formatMs(s.p75Millis()),
                formatMs(s.p90Millis()),
                formatMs(s.p95Millis()),
                formatMs(s.p99Millis()),
                formatMs(s.p999Millis()),
                formatMs(s.maxLatencyMillis())
        ));

        // Distribution Histogram
        sb.append(ANSI_BOLD).append("Distribution Histogram:\n").append(ANSI_RESET);
        sb.append(AsciiHistogramWidget.render(s.histogramBuckets(), s.totalRequests())).append("\n");

        // Status Badges
        sb.append(ANSI_BOLD).append("Status:\n").append(ANSI_RESET);
        sb.append("  ").append(ErrorSummaryWidget.render(s)).append("\n");
        sb.append(ANSI_GRAY).append("──────────────────────────────────────────────────────────────────────────────\n").append(ANSI_RESET);
        sb.append(ANSI_GRAY).append("Hotkeys: [+] / [-] Concurrency │ [p] Pause/Resume │ [q] Early Finish\n").append(ANSI_RESET);

        out.print(sb);
        out.flush();
    }

    private void renderHeadlessLine(PercentileSnapshot s) {
        long currentSec = (long) s.durationSeconds();
        if (currentSec > lastLogSecond) {
            lastLogSecond = currentSec;
            long totalSec = totalDuration.toSeconds();
            String line = String.format("[%02d:%02d/%02d:%02d] Req: %,9d │ RPS: %,7.0f │ p50: %6.2fms │ p90: %6.2fms │ p99: %6.2fms │ 2xx: %,d │ Err: %,d (%.2f%%)",
                    currentSec / 60, currentSec % 60,
                    totalSec / 60, totalSec % 60,
                    s.totalRequests(),
                    s.requestsPerSecond(),
                    s.p50Millis(),
                    s.p90Millis(),
                    s.p99Millis(),
                    s.status2xx(),
                    s.status4xx() + s.status5xx() + s.timeouts() + s.connectionErrors(),
                    s.errorRate() * 100.0
            );
            out.println(line);
            out.flush();
        }
    }

    public void renderFinalSummary(PercentileSnapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(ANSI_BOLD).append(ANSI_CYAN).append("============================ LOADPULSE BENCHMARK REPORT ============================\n").append(ANSI_RESET);
        sb.append(String.format("Target:            %s\n", targetDescription));
        sb.append(String.format("Concurrency:       %d Virtual Thread workers\n", concurrency));
        sb.append(String.format("Duration:          %.2f seconds\n", s.durationSeconds()));
        sb.append(String.format("Total Requests:    %,d\n", s.totalRequests()));
        sb.append(String.format("Throughput:        %,.2f req/sec (%.2f Mbps)\n", s.requestsPerSecond(), s.throughputMbps()));
        sb.append(String.format("Success Rate:      %.2f%% (2xx: %,d, 3xx: %,d, 4xx: %,d, 5xx: %,d)\n",
                (1.0 - s.errorRate()) * 100.0, s.status2xx(), s.status3xx(), s.status4xx(), s.status5xx()));
        sb.append(String.format("Timeouts/Errors:   Timeouts: %,d, Connection Errors: %,d\n", s.timeouts(), s.connectionErrors()));
        sb.append("------------------------------------------------------------------------------------\n");
        sb.append(ANSI_BOLD).append("Latency Percentiles:\n").append(ANSI_RESET);
        sb.append(String.format("  Min:             %8.3f ms\n", s.minLatencyMillis()));
        sb.append(String.format("  p50:             %8.3f ms\n", s.p50Millis()));
        sb.append(String.format("  p75:             %8.3f ms\n", s.p75Millis()));
        sb.append(String.format("  p90:             %8.3f ms\n", s.p90Millis()));
        sb.append(String.format("  p95:             %8.3f ms\n", s.p95Millis()));
        sb.append(String.format("  p99:             %8.3f ms\n", s.p99Millis()));
        sb.append(String.format("  p99.9:           %8.3f ms\n", s.p999Millis()));
        sb.append(String.format("  p99.99:          %8.3f ms\n", s.p9999Millis()));
        sb.append(String.format("  Max:             %8.3f ms\n", s.maxLatencyMillis()));
        sb.append("------------------------------------------------------------------------------------\n");
        sb.append(ANSI_BOLD).append("Distribution Histogram:\n").append(ANSI_RESET);
        sb.append(AsciiHistogramWidget.render(s.histogramBuckets(), s.totalRequests()));
        sb.append(ANSI_BOLD).append(ANSI_CYAN).append("====================================================================================\n").append(ANSI_RESET);

        out.print(sb);
        out.flush();
    }

    private String formatMs(double ms) {
        if (ms < 1.0) {
            return String.format("%.2fms", ms);
        } else if (ms < 10.0) {
            return String.format("%.2fms", ms);
        } else {
            return String.format("%.1fms", ms);
        }
    }

    @Override
    public void close() {
        if (interactive) {
            out.print(ANSI_SHOW_CURSOR);
            out.flush();
        }
    }
}
