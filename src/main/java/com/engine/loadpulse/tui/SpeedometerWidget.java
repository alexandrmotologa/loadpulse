package com.engine.loadpulse.tui;

public class SpeedometerWidget {
    private static final int GAUGE_WIDTH = 24;
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GRAY = "\u001B[90m";

    public static String render(double currentRps, double peakRps, double throughputBytesPerSec) {
        double maxExpected = Math.max(peakRps, Math.max(currentRps, 100.0));
        int filled = (int) Math.min(GAUGE_WIDTH, Math.round((currentRps / maxExpected) * GAUGE_WIDTH));

        double mbps = (throughputBytesPerSec * 8.0) / (1024.0 * 1024.0);

        StringBuilder sb = new StringBuilder();
        sb.append(ANSI_GRAY).append("[").append(ANSI_RESET);

        String color = filled > (GAUGE_WIDTH * 0.8) ? ANSI_GREEN : (filled > (GAUGE_WIDTH * 0.4) ? ANSI_CYAN : ANSI_YELLOW);
        sb.append(color);
        sb.append("━".repeat(Math.max(0, filled)));
        if (filled < GAUGE_WIDTH) {
            sb.append(ANSI_GRAY).append("─".repeat(GAUGE_WIDTH - filled));
        }
        sb.append(ANSI_GRAY).append("] ").append(ANSI_RESET);

        sb.append(String.format("%,9.0f req/s", currentRps));
        sb.append(ANSI_GRAY).append(" │ ").append(ANSI_RESET);
        sb.append(String.format("Peak: %,9.0f req/s", peakRps));
        sb.append(ANSI_GRAY).append(" │ ").append(ANSI_RESET);
        sb.append(String.format("%6.2f Mbps", mbps));

        return sb.toString();
    }
}
