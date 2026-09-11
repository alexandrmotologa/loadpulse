package com.engine.loadpulse.domain.model;

import java.time.Duration;

public record LoadStage(
        Duration duration,
        int targetConcurrency,
        int targetRps
) {
    public LoadStage {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            duration = Duration.ofSeconds(10);
        }
        if (targetConcurrency <= 0) {
            targetConcurrency = 10;
        }
        if (targetRps < 0) {
            targetRps = 0;
        }
    }

    public static LoadStage concurrency(Duration duration, int targetConcurrency) {
        return new LoadStage(duration, targetConcurrency, 0);
    }

    public static LoadStage rate(Duration duration, int targetRps) {
        return new LoadStage(duration, 10, targetRps);
    }
}
