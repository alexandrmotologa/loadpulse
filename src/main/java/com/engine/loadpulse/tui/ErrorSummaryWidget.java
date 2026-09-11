package com.engine.loadpulse.tui;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;

public class ErrorSummaryWidget {
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_MAGENTA = "\u001B[35m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";

    public static String render(PercentileSnapshot s) {
        StringBuilder sb = new StringBuilder();

        sb.append(ANSI_GREEN).append(ANSI_BOLD).append("2xx: ").append(ANSI_RESET)
                .append(String.format("%,d", s.status2xx())).append("  ");

        sb.append(ANSI_CYAN).append("3xx: ").append(ANSI_RESET)
                .append(String.format("%,d", s.status3xx())).append("  ");

        sb.append(s.status4xx() > 0 ? ANSI_YELLOW : ANSI_RESET).append("4xx: ")
                .append(String.format("%,d", s.status4xx())).append(ANSI_RESET).append("  ");

        sb.append(s.status5xx() > 0 ? ANSI_RED : ANSI_RESET).append("5xx: ")
                .append(String.format("%,d", s.status5xx())).append(ANSI_RESET).append("  ");

        sb.append(s.timeouts() > 0 ? ANSI_MAGENTA : ANSI_RESET).append("Timeouts: ")
                .append(String.format("%,d", s.timeouts())).append(ANSI_RESET).append("  ");

        sb.append(s.connectionErrors() > 0 ? ANSI_RED : ANSI_RESET).append("ConnErrors: ")
                .append(String.format("%,d", s.connectionErrors())).append(ANSI_RESET).append("  ");

        String errorRateColor = s.errorRate() > 0.05 ? ANSI_RED : (s.errorRate() > 0.0 ? ANSI_YELLOW : ANSI_GREEN);
        sb.append(errorRateColor).append(String.format("(Error Rate: %.2f%%)", s.errorRate() * 100.0)).append(ANSI_RESET);

        return sb.toString();
    }
}
