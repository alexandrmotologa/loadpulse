package com.engine.loadpulse.domain.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class DataFeed {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataFeedConfig(
            String file,
            String strategy
    ) {}

    private final List<Map<String, String>> rows;
    private final boolean randomStrategy;
    private final AtomicInteger index = new AtomicInteger(0);

    public DataFeed(List<Map<String, String>> rows, boolean randomStrategy) {
        this.rows = rows != null ? Collections.unmodifiableList(rows) : Collections.emptyList();
        this.randomStrategy = randomStrategy;
    }

    public static DataFeed fromConfig(DataFeedConfig config) {
        if (config == null || config.file() == null || config.file().isBlank()) {
            return null;
        }

        File file = new File(config.file());
        if (!file.exists() || !file.canRead()) {
            System.err.println("Warning: Data feed file not found: " + file.getAbsolutePath());
            return null;
        }

        try {
            List<Map<String, String>> parsed = parseCsv(file);
            boolean isRandom = "random".equalsIgnoreCase(config.strategy());
            return new DataFeed(parsed, isRandom);
        } catch (IOException e) {
            System.err.println("Warning: Failed to load data feed: " + e.getMessage());
            return null;
        }
    }

    public static List<Map<String, String>> parseCsv(File file) throws IOException {
        List<Map<String, String>> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return result;

            String[] headers = splitCsvLine(headerLine);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] values = splitCsvLine(line);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String h = headers[i].trim();
                    String v = i < values.length ? values[i].trim() : "";
                    row.put(h, v);
                }
                result.add(row);
            }
        }
        return result;
    }

    private static String[] splitCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens.toArray(new String[0]);
    }

    public Map<String, String> nextRow() {
        if (rows.isEmpty()) {
            return Collections.emptyMap();
        }

        if (randomStrategy) {
            int idx = ThreadLocalRandom.current().nextInt(rows.size());
            return rows.get(idx);
        } else {
            int idx = Math.abs(index.getAndIncrement() % rows.size());
            return rows.get(idx);
        }
    }

    public int getRowCount() {
        return rows.size();
    }
}
