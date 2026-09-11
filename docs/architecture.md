# LoadPulse Architecture and Design

LoadPulse is built around three core architectural tenets:
1. Virtual Thread concurrency to eliminate operating system thread context-switch overhead.
2. Zero-allocation telemetry to avoid garbage collection pauses skewing latency measurements.
3. Coordinated Omission compensation to record true client wait times under server saturation.

## Concurrency model

Traditional load testing tools in Java relied either on reactive event loops (Netty) or heavy thread pools (`java.lang.Thread`). Event loops are fast for non-blocking I/O but complicate scenario scripting with complex callback or reactive pipeline abstractions. Traditional thread pools consume 1 MB of stack memory per worker, limiting concurrency to a few hundred workers per gigabyte of RAM.

LoadPulse uses Java 21 Virtual Threads (`Thread.ofVirtual()`). Each concurrent worker runs on a lightweight virtual thread scheduled by the JVM on a small carrier thread pool. This architecture provides:
- Millions of potential concurrent workers with negligible memory footprint.
- Straightforward sequential blocking code (`clientPool.send()`, `rateLimiter.acquire()`, `Thread.sleep()`).
- High CPU cache efficiency during high-throughput network requests.

```
+-------------------------------------------------------------------+
|                        WorkerPool Core                            |
|                                                                   |
|   Worker 1 (VT)        Worker 2 (VT)        Worker N (VT)         |
|         │                    │                    │               |
|         ▼                    ▼                    ▼               |
|  RateLimiter.acquire() RateLimiter.acquire() RateLimiter.acquire()|
|         │                    │                    │               |
|         ▼                    ▼                    ▼               |
|  HttpClientPool.send() HttpClientPool.send() HttpClientPool.send()|
|         │                    │                    │               |
+─────────┼────────────────────┼────────────────────┼───────────────+
          ▼                    ▼                    ▼
+───────────────────────────────────────────────────────────────────+
|                  Target HTTP/1.1 or HTTP/2 Server                 |
+───────────────────────────────────────────────────────────────────+
```

## Latency measurement with HdrHistogram

Standard timing tools often collect raw latency arrays or compute quantiles over static arrays. This causes memory allocation in the request loop and triggers Garbage Collection (GC), which artificially inflates latency measurements.

LoadPulse uses Gil Tene's `org.HdrHistogram.Recorder`. HdrHistogram tracks measurements in a fixed-size integer array using a logarithmic bucket structure:
- Precision: 3 significant decimal digits across all ranges.
- Range: 1 microsecond (1,000 ns) up to 1 hour (3,600,000,000 µs).
- Allocation: 0 bytes during the benchmark loop.

### Coordinated Omission correction

When testing under fixed throughput (e.g. 10,000 RPS), requests are expected at fixed intervals (every 100 µs). If the server experiences a 100ms pause, workers queue up.

In uncorrected tools, the single request that hit the pause is recorded as taking 100ms, but the 1,000 requests that should have been sent during that window are never counted. This produces unrealistically optimistic p99 figures.

LoadPulse compensates for this by calling `recordLatencyWithExpectedInterval(latencyMicros, expectedIntervalMicros)`. When a request duration exceeds the expected interval, HdrHistogram automatically injects compensating samples representing the delayed requests, ensuring accurate percentile measurement under saturation.

## Live Terminal HUD architecture

The Terminal HUD operates decoupled from the worker threads:
1. Workers increment atomic counters and record latency in the `LatencyRecorder`.
2. A dedicated virtual thread fires at 10 FPS (every 100ms).
3. It takes an atomic snapshot of the histogram and counters without locking workers.
4. If running in an interactive TTY, it outputs ANSI escape sequences to redraw the dashboard.
5. If running in CI or non-interactive mode, it prints a single line per second.
