package com.engine.loadpulse.engine;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public class RateLimiter {
    private final AtomicLong intervalNanos;
    private final AtomicLong nextFreeTicketNanos;
    private volatile int currentRps;

    public RateLimiter(int targetRps) {
        if (targetRps <= 0) {
            throw new IllegalArgumentException("targetRps must be positive");
        }
        this.currentRps = targetRps;
        this.intervalNanos = new AtomicLong(1_000_000_000L / targetRps);
        this.nextFreeTicketNanos = new AtomicLong(System.nanoTime());
    }

    public void setTargetRps(int targetRps) {
        if (targetRps > 0) {
            this.currentRps = targetRps;
            this.intervalNanos.set(1_000_000_000L / targetRps);
        }
    }

    public int getTargetRps() {
        return currentRps;
    }

    public void acquire() {
        while (true) {
            long now = System.nanoTime();
            long currentNext = nextFreeTicketNanos.get();
            long interval = intervalNanos.get();
            long scheduledTime = Math.max(now, currentNext);
            long newNext = scheduledTime + interval;

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
        return intervalNanos.get();
    }

    public long getIntervalMicros() {
        return intervalNanos.get() / 1000L;
    }
}
