package com.engine.loadpulse.mock;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class MockBenchmarkServer implements AutoCloseable {
    private final HttpServer server;
    private final int port;
    private final Duration delay;
    private final Duration jitter;
    private final double errorRate;
    private final AtomicLong requestCounter = new AtomicLong(0);

    public MockBenchmarkServer(int port, Duration delay, Duration jitter, double errorRate) throws IOException {
        this.delay = delay != null ? delay : Duration.ZERO;
        this.jitter = jitter != null ? jitter : Duration.ZERO;
        this.errorRate = Math.max(0.0, Math.min(1.0, errorRate));

        this.server = HttpServer.create(new InetSocketAddress(port), 1000);
        this.server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.port = server.getAddress().getPort();

        setupHandlers();
    }

    public static MockBenchmarkServer startOnRandomPort(Duration delay, Duration jitter) throws IOException {
        MockBenchmarkServer mockServer = new MockBenchmarkServer(0, delay, jitter, 0.0);
        mockServer.start();
        return mockServer;
    }

    private void setupHandlers() {
        server.createContext("/", new BenchmarkHandler());
        server.createContext("/health", exchange -> {
            byte[] resp = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public int getPort() {
        return port;
    }

    public String getBaseUrl() {
        return "http://localhost:" + port;
    }

    public long getRequestCount() {
        return requestCounter.get();
    }

    @Override
    public void close() {
        stop();
    }

    private class BenchmarkHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            requestCounter.incrementAndGet();

            long totalDelayMs = delay.toMillis();
            if (!jitter.isZero()) {
                long maxJitter = jitter.toMillis();
                if (maxJitter > 0) {
                    long addedJitter = ThreadLocalRandom.current().nextLong(-maxJitter, maxJitter + 1);
                    totalDelayMs = Math.max(0, totalDelayMs + addedJitter);
                }
            }

            if (totalDelayMs > 0) {
                try {
                    Thread.sleep(totalDelayMs);
                } catch (InterruptedException ignored) {
                }
            }

            // Error injection check
            int statusCode = 200;
            byte[] responseBytes;
            if (errorRate > 0.0 && ThreadLocalRandom.current().nextDouble() < errorRate) {
                statusCode = 500;
                responseBytes = "{\"error\":\"Simulated mock server internal error\"}".getBytes(StandardCharsets.UTF_8);
            } else {
                responseBytes = "{\"status\":\"ok\",\"engine\":\"loadpulse-mock\"}".getBytes(StandardCharsets.UTF_8);
            }

            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
}
