package com.engine.loadpulse.engine;

import com.engine.loadpulse.domain.model.HttpMethod;
import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import com.engine.loadpulse.domain.scenario.DataFeed;
import com.engine.loadpulse.domain.scenario.DynamicPayloadGenerator;
import com.engine.loadpulse.domain.scenario.ResponseExtractor;
import com.engine.loadpulse.domain.scenario.ScenarioDefinition;
import com.engine.loadpulse.domain.scenario.ScenarioStep;
import com.engine.loadpulse.histogram.LatencyRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
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
    private final DataFeed dataFeed;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean measuring = new AtomicBoolean(false);

    public ScenarioExecutor(ScenarioDefinition scenario) {
        this.scenario = scenario;
        this.clientPool = new HttpClientPool(true, scenario.parseTimeout());
        this.recorder = new LatencyRecorder();
        this.payloadGenerator = new DynamicPayloadGenerator();
        this.rateLimiter = scenario.rps() > 0 ? new RateLimiter(scenario.rps()) : null;
        this.dataFeed = scenario.dataFeed() != null ? DataFeed.fromConfig(scenario.dataFeed()) : null;
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
                Map<String, String> sessionVars = new HashMap<>();
                try {
                    while (running.get()) {
                        Map<String, String> feedRow = dataFeed != null ? dataFeed.nextRow() : Collections.emptyMap();

                        if (rateLimiter != null) {
                            rateLimiter.acquire();
                        }

                        if (hasSteps) {
                            for (ScenarioStep step : steps) {
                                if (!running.get()) break;
                                executeStep(step, sessionVars, feedRow, expectedIntervalMicros);
                                if (step.thinkTimeMs() > 0) {
                                    Thread.sleep(step.thinkTimeMs());
                                }
                            }
                        } else {
                            executeSingleTarget(sessionVars, feedRow, expectedIntervalMicros);
                        }
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }
    }

    private void executeStep(
            ScenarioStep step,
            Map<String, String> sessionVars,
            Map<String, String> feedRow,
            long expectedIntervalMicros
    ) {
        String baseTarget = scenario.target();
        String path = step.path() != null ? payloadGenerator.interpolate(step.path(), sessionVars, feedRow) : "";
        String fullUrl = baseTarget != null ? (baseTarget.endsWith("/") ? baseTarget.substring(0, baseTarget.length() - 1) : baseTarget) + path : path;

        URI uri = URI.create(fullUrl);
        HttpMethod method = step.resolvedMethod();

        Map<String, String> interpolatedHeaders = new HashMap<>();
        if (step.headers() != null) {
            for (Map.Entry<String, String> entry : step.headers().entrySet()) {
                interpolatedHeaders.put(entry.getKey(), payloadGenerator.interpolate(entry.getValue(), sessionVars, feedRow));
            }
        }

        byte[] bodyBytes = null;
        if (step.body() != null && !step.body().isBlank()) {
            String interpolatedBody = payloadGenerator.interpolate(step.body(), sessionVars, feedRow);
            bodyBytes = interpolatedBody.getBytes(StandardCharsets.UTF_8);
        }

        HttpResponse<byte[]> response = executeSingleRequest(uri, method, interpolatedHeaders, bodyBytes, expectedIntervalMicros);

        if (response != null && step.extract() != null && !step.extract().isEmpty()) {
            Map<String, String> extracted = ResponseExtractor.extract(step.extract(), response, YAML_MAPPER);
            sessionVars.putAll(extracted);
        }
    }

    private void executeSingleTarget(
            Map<String, String> sessionVars,
            Map<String, String> feedRow,
            long expectedIntervalMicros
    ) {
        URI uri = URI.create(scenario.target());
        HttpMethod method = HttpMethod.fromString(scenario.method());

        Map<String, String> interpolatedHeaders = new HashMap<>();
        if (scenario.headers() != null) {
            for (Map.Entry<String, String> entry : scenario.headers().entrySet()) {
                interpolatedHeaders.put(entry.getKey(), payloadGenerator.interpolate(entry.getValue(), sessionVars, feedRow));
            }
        }

        byte[] bodyBytes = null;
        if (scenario.body() != null && !scenario.body().isBlank()) {
            bodyBytes = payloadGenerator.interpolate(scenario.body(), sessionVars, feedRow).getBytes(StandardCharsets.UTF_8);
        }

        executeSingleRequest(uri, method, interpolatedHeaders, bodyBytes, expectedIntervalMicros);
    }

    private HttpResponse<byte[]> executeSingleRequest(
            URI uri,
            HttpMethod method,
            Map<String, String> headers,
            byte[] bodyBytes,
            long expectedIntervalMicros
    ) {
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
            return response;
        } catch (HttpTimeoutException e) {
            long latencyMicros = (System.nanoTime() - start) / 1000L;
            if (record) {
                recorder.recordLatency(latencyMicros);
                recorder.recordTimeout();
            }
            return null;
        } catch (IOException | InterruptedException e) {
            long latencyMicros = (System.nanoTime() - start) / 1000L;
            if (record) {
                recorder.recordLatency(latencyMicros);
                recorder.recordConnectionError();
            }
            return null;
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
