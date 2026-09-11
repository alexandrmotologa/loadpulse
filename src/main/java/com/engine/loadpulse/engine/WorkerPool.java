package com.engine.loadpulse.engine;

import com.engine.loadpulse.domain.model.BenchmarkConfig;
import com.engine.loadpulse.domain.model.HttpMethod;
import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import com.engine.loadpulse.domain.scenario.DynamicPayloadGenerator;
import com.engine.loadpulse.histogram.LatencyRecorder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class WorkerPool implements AutoCloseable {
    private final BenchmarkConfig config;
    private final HttpClientPool clientPool;
    private final LatencyRecorder recorder;
    private final DynamicPayloadGenerator payloadGenerator;
    private final RateLimiter rateLimiter;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean measuring = new AtomicBoolean(false);

    public WorkerPool(BenchmarkConfig config) {
        this.config = config;
        this.clientPool = new HttpClientPool(config.http2(), config.requestTimeout());
        this.recorder = new LatencyRecorder();
        this.payloadGenerator = new DynamicPayloadGenerator();
        this.rateLimiter = config.targetRps() > 0 ? new RateLimiter(config.targetRps()) : null;
    }

    public PercentileSnapshot runBenchmark(Consumer<PercentileSnapshot> liveStatsConsumer) throws InterruptedException {
        running.set(true);
        int workers = config.concurrency();
        CountDownLatch finishLatch = new CountDownLatch(workers);

        long expectedIntervalMicros = rateLimiter != null ? rateLimiter.getIntervalMicros() : 0L;

        // Warm-up phase if configured
        if (!config.warmupDuration().isZero()) {
            measuring.set(false);
            startWorkerThreads(workers, finishLatch, expectedIntervalMicros);
            Thread.sleep(config.warmupDuration().toMillis());
            recorder.reset();
        }

        // Start measurement phase
        measuring.set(true);
        long startNano = System.nanoTime();

        // If no warmup was run, start worker threads now
        if (config.warmupDuration().isZero()) {
            startWorkerThreads(workers, finishLatch, expectedIntervalMicros);
        }

        // Timer thread or ticker for live stats
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

        // Sleep for the configured duration
        Thread.sleep(config.duration().toMillis());

        // Stop benchmark
        running.set(false);
        tickerThread.interrupt();

        finishLatch.await(5, TimeUnit.SECONDS);

        double totalElapsed = (System.nanoTime() - startNano) / 1_000_000_000.0;
        PercentileSnapshot finalSnapshot = recorder.createSnapshot(totalElapsed);
        if (liveStatsConsumer != null) {
            liveStatsConsumer.accept(finalSnapshot);
        }

        return finalSnapshot;
    }

    private void startWorkerThreads(int workers, CountDownLatch latch, long expectedIntervalMicros) {
        for (int i = 0; i < workers; i++) {
            Thread.ofVirtual().name("loadpulse-worker-", i + 1).start(() -> {
                try {
                    while (running.get()) {
                        if (rateLimiter != null) {
                            rateLimiter.acquire();
                        }
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

                int statusCode = response.statusCode();
                recorder.recordStatus(statusCode);

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

    public LatencyRecorder getRecorder() {
        return recorder;
    }

    @Override
    public void close() {
        running.set(false);
        clientPool.close();
    }
}
