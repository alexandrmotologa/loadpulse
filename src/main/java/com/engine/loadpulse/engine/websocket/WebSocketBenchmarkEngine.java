package com.engine.loadpulse.engine.websocket;

import com.engine.loadpulse.domain.metric.PercentileSnapshot;
import com.engine.loadpulse.histogram.LatencyRecorder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class WebSocketBenchmarkEngine implements AutoCloseable {
    private final URI targetUri;
    private final int concurrency;
    private final Duration duration;
    private final String messageToSend;
    private final Duration sendInterval;
    private final LatencyRecorder recorder;
    private final HttpClient httpClient;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public WebSocketBenchmarkEngine(
            URI targetUri,
            int concurrency,
            Duration duration,
            String messageToSend,
            Duration sendInterval
    ) {
        this.targetUri = targetUri;
        this.concurrency = Math.max(1, concurrency);
        this.duration = duration != null ? duration : Duration.ofSeconds(10);
        this.messageToSend = messageToSend != null ? messageToSend : "ping";
        this.sendInterval = sendInterval != null ? sendInterval : Duration.ofMillis(500);
        this.recorder = new LatencyRecorder();
        this.httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public PercentileSnapshot runBenchmark(Consumer<PercentileSnapshot> liveStatsConsumer) throws InterruptedException {
        running.set(true);
        CountDownLatch finishLatch = new CountDownLatch(concurrency);
        long startNano = System.nanoTime();

        for (int i = 0; i < concurrency; i++) {
            final int workerId = i + 1;
            Thread.ofVirtual().name("loadpulse-ws-worker-", workerId).start(() -> {
                try {
                    connectAndLoop();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        Thread tickerThread = Thread.ofVirtual().name("loadpulse-ws-ticker").start(() -> {
            while (running.get()) {
                try {
                    Thread.sleep(100);
                    if (liveStatsConsumer != null) {
                        double elapsed = (System.nanoTime() - startNano) / 1_000_000_000.0;
                        liveStatsConsumer.accept(recorder.createSnapshot(elapsed));
                    }
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        });

        Thread.sleep(duration.toMillis());

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

    private void connectAndLoop() {
        long connectStart = System.nanoTime();
        ConcurrentHashMap<String, Long> sentTimes = new ConcurrentHashMap<>();

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                long handshakeMicros = (System.nanoTime() - connectStart) / 1000L;
                recorder.recordLatency(handshakeMicros);
                recorder.recordStatus(101); // 101 Switching Protocols
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                Long sentTime = sentTimes.remove(data.toString());
                if (sentTime != null) {
                    long rttMicros = (System.nanoTime() - sentTime) / 1000L;
                    recorder.recordLatency(rttMicros);
                    recorder.recordStatus(200);
                } else {
                    recorder.recordStatus(200);
                }
                recorder.recordBytes(data.length());
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                recorder.recordConnectionError();
            }
        };

        try {
            WebSocket ws = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(targetUri, listener)
                    .get(5, TimeUnit.SECONDS);

            while (running.get()) {
                long sendStart = System.nanoTime();
                sentTimes.put(messageToSend, sendStart);
                ws.sendText(messageToSend, true);

                Thread.sleep(sendInterval.toMillis());
            }

            ws.sendClose(WebSocket.NORMAL_CLOSURE, "Benchmark complete").get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            recorder.recordConnectionError();
        }
    }

    public LatencyRecorder getRecorder() {
        return recorder;
    }

    @Override
    public void close() {
        running.set(false);
    }
}
