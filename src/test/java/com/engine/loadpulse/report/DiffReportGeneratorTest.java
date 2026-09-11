package com.engine.loadpulse.report;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DiffReportGeneratorTest {

    private PercentileSnapshot createSnapshot(double rps, long p99Micros, double errorRate) {
        return new PercentileSnapshot(
                1000L,
                10.0,
                rps,
                50000.0,
                1000L,
                p99Micros * 2,
                p99Micros / 2.0,
                100.0,
                p99Micros / 3,
                p99Micros / 2,
                (long) (p99Micros * 0.8),
                (long) (p99Micros * 0.9),
                p99Micros,
                p99Micros + 1000,
                p99Micros + 2000,
                1000L,
                0L,
                0L,
                0L,
                0L,
                0L,
                errorRate,
                Collections.emptyMap(),
                Map.of("0-1ms", 1000L)
        );
    }

    @Test
    @DisplayName("Should generate console diff and standalone HTML diff report")
    void testDiffReporting(@TempDir Path tempDir) throws Exception {
        PercentileSnapshot before = createSnapshot(1000.0, 20_000L, 0.0);
        PercentileSnapshot after = createSnapshot(1200.0, 18_000L, 0.0); // improved RPS and p99

        String consoleDiff = DiffReportGenerator.renderConsoleDiff(before, after, "v1.0", "v1.1");
        assertThat(consoleDiff).contains("LOADPULSE REGRESSION DIFF");
        assertThat(consoleDiff).contains("Throughput (RPS)");
        assertThat(consoleDiff).contains("[IMPROVED]");

        Path htmlPath = tempDir.resolve("diff-report.html");
        DiffReportGenerator.generateHtmlDiffReport(before, after, "v1.0", "v1.1", htmlPath);

        assertThat(Files.exists(htmlPath)).isTrue();
        String html = Files.readString(htmlPath);
        assertThat(html).contains("LoadPulse Performance Regression Diff");
        assertThat(html).contains("v1.0");
        assertThat(html).contains("v1.1");
    }
}
