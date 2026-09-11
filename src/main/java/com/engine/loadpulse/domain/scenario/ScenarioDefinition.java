package com.engine.loadpulse.domain.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScenarioDefinition(
        String name,
        String target,
        String method,
        int concurrency,
        String duration,
        String warmup,
        int rps,
        Map<String, String> headers,
        String body,
        String timeout,
        List<ScenarioStep> steps,
        String assertion
) {
    public ScenarioDefinition {
        if (concurrency <= 0) {
            concurrency = 50;
        }
        if (duration == null || duration.isBlank()) {
            duration = "10s";
        }
        if (warmup == null || warmup.isBlank()) {
            warmup = "0s";
        }
        headers = headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
        steps = steps != null ? Collections.unmodifiableList(steps) : Collections.emptyList();
    }

    public Duration parseDuration() {
        return parseTimeDuration(duration, Duration.ofSeconds(10));
    }

    public Duration parseWarmup() {
        return parseTimeDuration(warmup, Duration.ZERO);
    }

    public Duration parseTimeout() {
        return parseTimeDuration(timeout, Duration.ofSeconds(10));
    }

    public static Duration parseTimeDuration(String text, Duration defaultVal) {
        if (text == null || text.isBlank()) {
            return defaultVal;
        }
        text = text.trim().toLowerCase();
        try {
            if (text.endsWith("ms")) {
                long ms = Long.parseLong(text.substring(0, text.length() - 2).trim());
                return Duration.ofMillis(ms);
            } else if (text.endsWith("s")) {
                long s = Long.parseLong(text.substring(0, text.length() - 1).trim());
                return Duration.ofSeconds(s);
            } else if (text.endsWith("m")) {
                long m = Long.parseLong(text.substring(0, text.length() - 1).trim());
                return Duration.ofMinutes(m);
            } else if (text.endsWith("h")) {
                long h = Long.parseLong(text.substring(0, text.length() - 1).trim());
                return Duration.ofHours(h);
            } else {
                long s = Long.parseLong(text);
                return Duration.ofSeconds(s);
            }
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }
}
