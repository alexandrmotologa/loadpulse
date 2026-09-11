package com.engine.loadpulse.report;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SlaAssertionEvaluator {
    private static final Pattern CONDITION_PATTERN = Pattern.compile(
            "([a-zA-Z0-9_]+)\\s*(<=|>=|==|!=|<|>)\\s*([0-9.]+(?:ms|s|%)?)"
    );

    public record AssertionResult(
            boolean passed,
            List<String> details,
            List<String> failures
    ) {
        public String summary() {
            if (passed) {
                return "All SLA assertions passed successfully (" + details.size() + " checked).";
            } else {
                return "SLA assertion failed: " + String.join("; ", failures);
            }
        }
    }

    public static AssertionResult evaluate(String expression, PercentileSnapshot snapshot) {
        if (expression == null || expression.isBlank()) {
            return new AssertionResult(true, List.of("No SLA assertions specified"), List.of());
        }

        List<String> details = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        String[] clauses = expression.split("&&");

        for (String rawClause : clauses) {
            String clause = rawClause.trim();
            if (clause.isEmpty()) continue;

            Matcher matcher = CONDITION_PATTERN.matcher(clause);
            if (!matcher.matches()) {
                failures.add("Invalid syntax in assertion clause: '" + clause + "'");
                continue;
            }

            String metric = matcher.group(1).toLowerCase();
            String operator = matcher.group(2);
            String thresholdStr = matcher.group(3);

            double actualValue = resolveMetric(metric, snapshot);
            double expectedValue = parseThreshold(thresholdStr);

            boolean clausePassed = switch (operator) {
                case "<" -> actualValue < expectedValue;
                case "<=" -> actualValue <= expectedValue;
                case ">" -> actualValue > expectedValue;
                case ">=" -> actualValue >= expectedValue;
                case "==" -> Math.abs(actualValue - expectedValue) < 0.0001;
                case "!=" -> Math.abs(actualValue - expectedValue) >= 0.0001;
                default -> false;
            };

            String detail = String.format("%s (actual: %.3f, condition: %s %.3f) -> %s",
                    metric, actualValue, operator, expectedValue, clausePassed ? "PASSED" : "FAILED");
            details.add(detail);

            if (!clausePassed) {
                failures.add(String.format("Violated: %s %s %s (actual value was %.3f)",
                        metric, operator, thresholdStr, actualValue));
            }
        }

        return new AssertionResult(failures.isEmpty(), details, failures);
    }

    private static double resolveMetric(String metric, PercentileSnapshot s) {
        return switch (metric) {
            case "p50" -> s.p50Millis();
            case "p75" -> s.p75Millis();
            case "p90" -> s.p90Millis();
            case "p95" -> s.p95Millis();
            case "p99" -> s.p99Millis();
            case "p999", "p99.9" -> s.p999Millis();
            case "p9999", "p99.99" -> s.p9999Millis();
            case "max" -> s.maxLatencyMillis();
            case "min" -> s.minLatencyMillis();
            case "mean" -> s.meanLatencyMillis();
            case "rps", "throughput" -> s.requestsPerSecond();
            case "error_rate" -> s.errorRate();
            case "total_requests" -> s.totalRequests();
            case "status2xx" -> s.status2xx();
            case "status5xx" -> s.status5xx();
            default -> 0.0;
        };
    }

    private static double parseThreshold(String val) {
        val = val.trim().toLowerCase();
        if (val.endsWith("ms")) {
            return Double.parseDouble(val.substring(0, val.length() - 2));
        } else if (val.endsWith("s")) {
            return Double.parseDouble(val.substring(0, val.length() - 1)) * 1000.0; // convert s to ms
        } else if (val.endsWith("%")) {
            return Double.parseDouble(val.substring(0, val.length() - 1)) / 100.0;
        } else {
            return Double.parseDouble(val);
        }
    }
}
