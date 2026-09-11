package com.engine.loadpulse.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    @DisplayName("Should accurately pace requests at target RPS")
    void testRateLimitingPacing() throws InterruptedException {
        int targetRps = 200; // 200 req/sec = 1 req every 5ms
        RateLimiter limiter = new RateLimiter(targetRps);

        int requests = 20; // should take ~100ms
        long start = System.currentTimeMillis();

        for (int i = 0; i < requests; i++) {
            limiter.acquire();
        }

        long elapsed = System.currentTimeMillis() - start;

        // 20 requests at 200 RPS should take around 95ms to 130ms
        assertThat(elapsed).isGreaterThanOrEqualTo(85L);
        assertThat(limiter.getIntervalMicros()).isEqualTo(5000L);
    }
}
