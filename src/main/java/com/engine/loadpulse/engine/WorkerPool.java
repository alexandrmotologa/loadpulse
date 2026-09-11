package com.engine.loadpulse.engine;

import com.engine.loadpulse.domain.model.BenchmarkConfig;
import com.engine.loadpulse.domain.model.HttpMethod;
import com.engine.loadpulse.domain.model.LoadStage;
import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import com.engine.loadpulse.domain.scenario.DynamicPayloadGenerator;
import com.engine.loadpulse.histogram.LatencyRecorder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public class WorkerPool implements AutoCloseable {
    private static final int MAX_WORKERS = 10_000;

    private final BenchmarkConfig config;
    private final HttpClientPool clientPool;
    private final LatencyRecorder recorder;
    private final DynamicPayloadGenerator payloadGenerator;
    private final RateLimiter rateLimiter;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean measuring = new AtomicBoolean(false);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicInteger activeTargetWorkers;

    public WorkerPool(BenchmarkConfig config) {
        this.config = config;
        this.clientPool = new HttpClientPool(config.http2(), config.requestTimeout());
        this.recorder = new LatencyRecorder();
        this.payloadGenerator = new DynamicPayloadGenerator();
        this.rateLimiter = config.targetRps() > 0 ? new RateLimiter(config.targetRps()) : null;
        this.activeTargetWorkers = new AtomicInteger(
                !config.rampUpDuration().isZero() ? 1 : config.concurrency()
        );
    }

    public PercentileSnapshot runBenchmark(Consumer<PercentileSnapshot> liveStatsConsumer) throws InterruptedException {
        running.set(true);
        int maxWorkers = Math.max(config.concurrency() * 2, 100);
        CountDownLatch finishLatch = new CountDownLatch(maxWorkers);

        long expectedIntervalMicros = rateLimiter != null ? rateLimiter.getIntervalMicros() : 0L;

        // Warm-up phase if configured
        if (!config.warmupDuration().isZero()) {
            measuring.set(false);
            startWorkerThreads(maxWorkers, finishLatch);
            Thread.sleep(config.warmupDuration().toMillis());
            recorder.reset();
        }

        // Start measurement phase
        measuring.set(true);
        long startNano = System.nanoTime();

        // If no warmup was run, start worker threads now
        if (config.warmupDuration().isZero()) {
            startWorkerThreads(maxWorkers, finishLatch);
        }

        // Handle ramp-up or stages asynchronously
        Thread profileThread = Thread.ofVirtual().name("loadpulse-profiler").start(() -> {
            try {
                if (!config.stages().isEmpty()) {
                    executeStages(config.stages());
                } else if (!config.rampUpDuration().isZero()) {
                    executeRampUp(config.rampUpDuration(), config.concurrency());
                }
            } catch (InterruptedException ignored) {
            }
        });

        // Ticker thread for live stats
        Thread tickerThread = Thread.ofVirtual().name("loadpulse-ticker").start(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(100); // 10 FPS
                    if (liveStatsConsumer != null && measuring.get()) {
                        double elapsed = (System.nanoTime() - startNano) / 1_000_000_000.0;
                        liveStatsConsumer.accept(recorder.createSnapshot(elapsed));
                    }
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        });

        // Run until duration elapsed or requested stop
        long totalDurationMs = computeTotalDurationMs();
        long deadlineNano = startNano + TimeUnit.MILLISECONDS.toNanos(totalDurationMs);

        while (running.get() && System.nanoTime() < deadlineNano) {
            Thread.sleep(50);
        }

        // Stop benchmark
        running.set(false);
        profileThread.interrupt();
        tickerThread.interrupt();

        finishLatch.await(3, TimeUnit.SECONDS);

        double totalElapsed = (System.nanoTime() - startNano) / 1_000_000_000.0;
        PercentileSnapshot finalSnapshot = recorder.createSnapshot(totalElapsed);
        if (liveStatsConsumer != null) {
            liveStatsConsumer.accept(finalSnapshot);
        }

        return finalSnapshot;
    }

    private void executeRampUp(Duration rampDuration, int finalConcurrency) throws InterruptedException {
        long rampMs = rampDuration.toMillis();
        long steps = Math.min(finalConcurrency, 50);
        long intervalMs = rampMs / Math.max(1, steps);

        for (int i = 1; i <= steps; i++) {
            if (!running.get()) break;
            int target = (int) Math.round(((double) i / steps) * finalConcurrency);
            activeTargetWorkers.set(Math.max(1, target));
            Thread.sleep(intervalMs);
        }
        activeTargetWorkers.set(finalConcurrency);
    }

    private void executeStages(List<LoadStage> stages) throws InterruptedException {
        for (LoadStage stage : stages) {
            if (!running.get()) break;
            activeTargetWorkers.set(stage.targetConcurrency());
            if (rateLimiter != null && stage.targetRps() > 0) {
                rateLimiter.setTargetRps(stage.targetRps());
            }
            Thread.sleep(stage.duration().toMillis());
        }
    }

    private long computeTotalDurationMs() {
        if (!config.stages().isEmpty()) {
            long sum = 0;
            for (LoadStage stage : config.stages()) {
                sum += stage.duration().toMillis();
            }
            return sum;
        }
        return config.duration().toMillis() + config.rampUpDuration().toMillis();
    }

    private void startWorkerThreads(int totalWorkerCount, CountDownLatch latch) {
        for (int i = 0; i < totalWorkerCount; i++) {
            final int workerIndex = i;
            Thread.ofVirtual().name("loadpulse-worker-", workerIndex + 1).start(() -> {
                try {
                    while (running.get()) {
                        if (paused.get()) {
                            LockSupport.parkNanos(20_000_000L); // 20ms
                            continue;
                        }

                        if (workerIndex >= activeTargetWorkers.get()) {
                            LockSupport.parkNanos(20_000_000L);
                            continue;
                        }

                        if (rateLimiter != null) {
                            rateLimiter.acquire();
                        }

                        long expectedIntervalMicros = rateLimiter != null ? rateLimiter.getIntervalMicros() : 0L;
                        executeRequest(expectedIntervalMicros);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
    }

    private void executeRequest(long expectedIntervalMicros) {
        boolean record = measuring.get();
        URI targetUri = config.targetUri();
        HttpMethod method = config.method();

        byte[] bodyBytes = null;
        if (config.body() != null && !config.body().isBlank()) {
            String interpolated = payloadGenerator.interpolate(config.body());
            bodyBytes = interpolated.getBytes(StandardCharsets.UTF_8);
        }

        HttpRequest request = HttpClientPool.createRequest(
                targetUri,
                method,
                config.headers(),
                bodyBytes,
                config.requestTimeout()
        );

        long start = System.nanoTime();
        try {
            HttpResponse<byte[]> response = clientPool.send(request);
            long latencyMicros = (System.nanoTime() - start) / 1000L;

            if (record) {
                if (expectedIntervalMicros > 0) {
                    recorder.recordLatencyWithExpectedInterval(latencyMicros, expectedIntervalMicros);
                } else {
                    recorder.recordLatency(latencyMicros);
                }

                recorder.recordStatus(response.statusCode());

                byte[] body = response.body();
                if (body != null) {
                    recorder.recordBytes(body.length);
                }
            }
        } catch (HttpTimeoutException e) {
            long latencyMicros = (System.nanoTime() - start) / 1000L;
            if (record) {
                recorder.recordLatency(latencyMicros);
                recorder.recordTimeout();
            }
        } catch (IOException | InterruptedException e) {
            long latencyMicros = (System.nanoTime() - start) / 1000L;
            if (record) {
                recorder.recordLatency(latencyMicros);
                recorder.recordConnectionError();
            }
        }
    }

    // Dynamic control methods for interactive TUI hotkeys
    public void togglePause() {
        paused.set(!paused.get());
    }

    public boolean isPaused() {
        return paused.get();
    }

    public void adjustConcurrency(int delta) {
        activeTargetWorkers.updateAndGet(curr -> Math.max(1, Math.min(MAX_WORKERS, curr + delta)));
    }

    public void adjustRps(int delta) {
        if (rateLimiter != null) {
            int current = rateLimiter.getTargetRps();
            int updated = Math.max(1, current + delta);
            rateLimiter.setTargetRps(updated);
        }
    }

    public int getActiveTargetWorkers() {
        return activeTargetWorkers.get();
    }

    public void requestStop() {
        running.set(false);
    }

    public LatencyRecorder getRecorder() {
        return recorder;
    }

    @Override
    public void close() {
        running.set(false);
        clientPool.close();
    }
}
