package com.engine.loadpulse.tui;

import java.util.Map;

public class AsciiHistogramWidget {
    private static final int MAX_BAR_WIDTH = 25;
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GRAY = "\u001B[90m";

    public static String render(Map<String, Long> buckets, long totalRequests) {
        if (buckets == null || buckets.isEmpty() || totalRequests <= 0) {
            return ANSI_GRAY + "  (No latency data recorded yet)" + ANSI_RESET;
        }

        StringBuilder sb = new StringBuilder();
        long maxCount = 1;
        for (Long count : buckets.values()) {
            if (count > maxCount) {
                maxCount = count;
            }
        }

        for (Map.Entry<String, Long> entry : buckets.entrySet()) {
            String label = entry.getKey();
            long count = entry.getValue();
            double pct = (double) count / totalRequests * 100.0;
            int barLength = (int) Math.round(((double) count / maxCount) * MAX_BAR_WIDTH);

            sb.append(String.format("  %-10s ", label));
            sb.append(ANSI_CYAN);
            sb.append("█".repeat(Math.max(0, barLength)));
            sb.append(ANSI_RESET);
            if (barLength < MAX_BAR_WIDTH) {
                sb.append(" ".repeat(MAX_BAR_WIDTH - barLength));
            }
            sb.append(String.format(" %6d (%5.1f%%)\n", count, pct));
        }

        return sb.toString();
    }
}
