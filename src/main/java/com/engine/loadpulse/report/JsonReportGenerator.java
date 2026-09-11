package com.engine.loadpulse.report;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonReportGenerator {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void generateReport(PercentileSnapshot snapshot, String target, int concurrency, Path outputPath) throws IOException {
        if (outputPath == null) {
            return;
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("engine", "loadpulse");
        root.put("version", "1.0.0");
        root.put("target", target);
        root.put("concurrency", concurrency);
        root.put("snapshot", snapshot);

        File file = outputPath.toFile();
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        MAPPER.writeValue(file, root);
    }
}
