package com.engine.loadpulse.engine;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class RateLimiter {
    private final long intervalNanos;
    private final AtomicLong nextFreeTicketNanos;

    public RateLimiter(int targetRps) {
        if (targetRps <= 0) {
            throw new IllegalArgumentException("targetRps must be positive");
        }
        this.intervalNanos = 1_000_000_000L / targetRps;
        this.nextFreeTicketNanos = new AtomicLong(System.nanoTime());
    }

    public void acquire() {
        while (true) {
            long now = System.nanoTime();
            long currentNext = nextFreeTicketNanos.get();
            long scheduledTime = Math.max(now, currentNext);
            long newNext = scheduledTime + intervalNanos;

            if (nextFreeTicketNanos.compareAndSet(currentNext, newNext)) {
                long waitNanos = scheduledTime - now;
                if (waitNanos > 0) {
                    LockSupport.parkNanos(waitNanos);
                }
                return;
            }
        }
    }

    public long getIntervalNanos() {
        return intervalNanos;
    }

    public long getIntervalMicros() {
        return intervalNanos / 1000L;
    }
}
