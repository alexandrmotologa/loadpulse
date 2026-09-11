package com.engine.loadpulse.engine;

import com.engine.loadpulse.domain.model.BenchmarkConfig;
import com.engine.loadpulse.domain.model.HttpMethod;
import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import com.engine.loadpulse.domain.scenario.DynamicPayloadGenerator;
import com.engine.loadpulse.domain.scenario.ScenarioDefinition;
import com.engine.loadpulse.domain.scenario.ScenarioStep;
import com.engine.loadpulse.histogram.LatencyRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class ScenarioExecutor implements AutoCloseable {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule());

    private final ScenarioDefinition scenario;
    private final HttpClientPool clientPool;
    private final LatencyRecorder recorder;
    private final DynamicPayloadGenerator payloadGenerator;
    private final RateLimiter rateLimiter;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean measuring = new AtomicBoolean(false);

    public ScenarioExecutor(ScenarioDefinition scenario) {
        this.scenario = scenario;
        this.clientPool = new HttpClientPool(true, scenario.parseTimeout());
        this.recorder = new LatencyRecorder();
        this.payloadGenerator = new DynamicPayloadGenerator();
        this.rateLimiter = scenario.rps() > 0 ? new RateLimiter(scenario.rps()) : null;
    }

    public static ScenarioDefinition loadFromFile(File yamlFile) throws IOException {
        return YAML_MAPPER.readValue(yamlFile, ScenarioDefinition.class);
    }

    public static ScenarioDefinition loadFromString(String yamlContent) throws IOException {
        return YAML_MAPPER.readValue(yamlContent, ScenarioDefinition.class);
    }

    public PercentileSnapshot execute(Consumer<PercentileSnapshot> liveStatsConsumer) throws InterruptedException {
        running.set(true);
        int workers = scenario.concurrency();
        CountDownLatch finishLatch = new CountDownLatch(workers);

        long expectedIntervalMicros = rateLimiter != null ? rateLimiter.getIntervalMicros() : 0L;

        Duration warmup = scenario.parseWarmup();
        if (!warmup.isZero()) {
            measuring.set(false);
            startScenarioWorkers(workers, finishLatch, expectedIntervalMicros);
            Thread.sleep(warmup.toMillis());
            recorder.reset();
        }

        measuring.set(true);
        long startNano = System.nanoTime();

        if (warmup.isZero()) {
            startScenarioWorkers(workers, finishLatch, expectedIntervalMicros);
        }

        Thread tickerThread = Thread.ofVirtual().name("loadpulse-scenario-ticker").start(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(100);
                    if (liveStatsConsumer != null && measuring.get()) {
                        double elapsed = (System.nanoTime() - startNano) / 1_000_000_000.0;
                        liveStatsConsumer.accept(recorder.createSnapshot(elapsed));
                    }
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        });

        Thread.sleep(scenario.parseDuration().toMillis());

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

    private void startScenarioWorkers(int workers, CountDownLatch latch, long expectedIntervalMicros) {
        List<ScenarioStep> steps = scenario.steps();
        boolean hasSteps = steps != null && !steps.isEmpty();

        for (int i = 0; i < workers; i++) {
            Thread.ofVirtual().name("loadpulse-scenario-worker-", i + 1).start(() -> {
                try {
                    while (running.get()) {
                        if (rateLimiter != null) {
                            rateLimiter.acquire();
                        }

                        if (hasSteps) {
                            for (ScenarioStep step : steps) {
                                if (!running.get()) break;
                                executeStep(step, expectedIntervalMicros);
                                if (step.thinkTimeMs() > 0) {
                                    Thread.sleep(step.thinkTimeMs());
                                }
                            }
                        } else {
                            executeSingleTarget(expectedIntervalMicros);
                        }
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }
    }

    private void executeStep(ScenarioStep step, long expectedIntervalMicros) {
        String baseTarget = scenario.target();
        String path = step.path() != null ? step.path() : "";
        String fullUrl = baseTarget != null ? (baseTarget.endsWith("/") ? baseTarget.substring(0, baseTarget.length() - 1) : baseTarget) + path : path;

        URI uri = URI.create(fullUrl);
        HttpMethod method = step.resolvedMethod();

        byte[] bodyBytes = null;
        if (step.body() != null && !step.body().isBlank()) {
            bodyBytes = payloadGenerator.interpolate(step.body()).getBytes(StandardCharsets.UTF_8);
        }

        executeSingleRequest(uri, method, step.headers(), bodyBytes, expectedIntervalMicros);
    }

    private void executeSingleTarget(long expectedIntervalMicros) {
        URI uri = URI.create(scenario.target());
        HttpMethod method = HttpMethod.fromString(scenario.method());

        byte[] bodyBytes = null;
        if (scenario.body() != null && !scenario.body().isBlank()) {
            bodyBytes = payloadGenerator.interpolate(scenario.body()).getBytes(StandardCharsets.UTF_8);
        }

        executeSingleRequest(uri, method, scenario.headers(), bodyBytes, expectedIntervalMicros);
    }

    private void executeSingleRequest(URI uri, HttpMethod method, Map<String, String> headers, byte[] bodyBytes, long expectedIntervalMicros) {
        boolean record = measuring.get();
        HttpRequest request = HttpClientPool.createRequest(
                uri,
                method,
                headers,
                bodyBytes,
                scenario.parseTimeout()
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
                if (response.body() != null) {
                    recorder.recordBytes(response.body().length);
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

    public ScenarioDefinition getScenario() {
        return scenario;
    }

    @Override
    public void close() {
        running.set(false);
        clientPool.close();
    }
}
