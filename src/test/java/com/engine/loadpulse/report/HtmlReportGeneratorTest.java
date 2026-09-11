package com.engine.loadpulse.report;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlReportGeneratorTest {

    @Test
    @DisplayName("Should generate complete standalone HTML report with embedded SVG charts")
    void testHtmlReportGeneration(@TempDir Path tempDir) throws Exception {
        Map<String, Long> buckets = new LinkedHashMap<>();
        buckets.put("0-1ms", 800L);
        buckets.put("1-5ms", 150L);
        buckets.put("5-15ms", 50L);

        PercentileSnapshot snapshot = new PercentileSnapshot(
                1000L,
                10.0,
                100.0,
                102400.0,
                500L,
                12000L,
                2100.0,
                500.0,
                1500L,
                2000L,
                4000L,
                6000L,
                9000L,
                11000L,
                12000L,
                1000L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0.0,
                Collections.emptyMap(),
                buckets
        );

        Path reportFile = tempDir.resolve("report.html");
        SlaAssertionEvaluator.AssertionResult slaResult = new SlaAssertionEvaluator.AssertionResult(true, Collections.emptyList(), Collections.emptyList());

        HtmlReportGenerator.generateReport(
                snapshot,
                "http://localhost:8080/api/orders",
                50,
                "p99 < 50ms",
                slaResult,
                reportFile
        );

        assertThat(Files.exists(reportFile)).isTrue();
        String content = Files.readString(reportFile);

        assertThat(content).contains("LoadPulse Benchmark Report");
        assertThat(content).contains("http://localhost:8080/api/orders");
        assertThat(content).contains("SLA PASSED");
        assertThat(content).contains("<svg");
        assertThat(content).contains("1,000 requests");
    }
}
