<p align="center">
  <img src="docs/images/logo.png?raw=true" alt="LoadPulse Logo" width="130" style="border-radius: 24px;" />
</p>

<h1 align="center">LoadPulse</h1>

<p align="center">
  <a href="https://github.com/alexandrmotologa/loadpulse/actions"><img src="https://github.com/alexandrmotologa/loadpulse/actions/workflows/ci.yml/badge.svg" alt="Build Status" /></a>
  <img src="https://img.shields.io/badge/Java-21%20LTS-orange.svg" alt="Java 21" />
  <img src="https://img.shields.io/badge/HdrHistogram-2.2.2-blue.svg" alt="HdrHistogram" />
  <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="MIT License" />
</p>

<p align="center">
  <strong>Reactive HTTP/1.1, HTTP/2, and gRPC load testing engine powered by Java 21 Virtual Threads, microsecond-accurate HdrHistogram telemetry, live ANSI terminal HUD, YAML scenarios, and CI/CD SLA gating.</strong>
</p>

<p align="center">
  <a href="#core-capabilities">Capabilities</a> &bull;
  <a href="#visual-tour">Visual Tour</a> &bull;
  <a href="#quickstart">Quickstart</a> &bull;
  <a href="#architecture">Architecture</a> &bull;
  <a href="#terminal-hud">Terminal HUD</a> &bull;
  <a href="#yaml-scenarios">YAML Scenarios</a> &bull;
  <a href="#regression-diff">Regression Diff</a> &bull;
  <a href="#html-reports">HTML Reports</a> &bull;
  <a href="#verification-and-testing">Testing</a>
</p>

---

LoadPulse is an open source load testing engine designed for low-latency API benchmarking. It replaces legacy tools that run blindly without live feedback or require heavy JavaScript runtimes.

Using Project Loom Virtual Threads (`java.lang.Thread.ofVirtual()`), LoadPulse runs thousands of concurrent requests with low memory overhead on standard developer machines. Latencies are recorded with microsecond accuracy using HdrHistogram, compensating for Coordinated Omission to guarantee realistic p99 and p99.9 measurements.

## Visual tour

### 1. Live interactive ANSI terminal HUD

High-refresh dashboard displaying throughput speedometers, microsecond latency matrices, dynamic Unicode distribution histograms, and live hotkey controls (`+`, `-`, `p`, `q`).

<p align="center">
  <img src="docs/images/screenshot-terminal.png?raw=true" alt="LoadPulse Terminal HUD" width="900" style="border-radius: 8px;" />
</p>

### 2. Standalone offline HTML benchmark reports

Zero-dependency single-file reports containing logarithmic SVG percentile ladders, latency bucket distributions, status breakdowns, and SLA evaluation badges.

<p align="center">
  <img src="docs/images/screenshot-html-report.png?raw=true" alt="LoadPulse HTML Report" width="900" style="border-radius: 8px;" />
</p>

### 3. Automated performance regression diff analysis

Side-by-side comparison between baseline and candidate benchmark runs with percentage deltas and threshold breach alerts for CI pipelines.

<p align="center">
  <img src="docs/images/screenshot-diff.png?raw=true" alt="LoadPulse Regression Diff" width="900" style="border-radius: 8px;" />
</p>

## Core capabilities

- Virtual Thread worker engine: spawns thousands of concurrent workers without OS thread exhaustion or memory bloat.
- Dual workload models: supports closed concurrency loops (`-c 500`) and open rate-paced generation (`--rps 10000`) via a nano-precision token bucket rate limiter.
- Load stages and ramp profiles: define multi-phase traffic ramp-up and ramp-down shapes (`--stages "30s:50,1m:200,30s:0"` or `--ramp-up 10s`).
- Interactive TUI hotkeys: dynamically scale concurrency (`+` / `-`), pause traffic (`p`), or gracefully terminate (`q`) during a live run.
- Microsecond latency telemetry: integrates HdrHistogram with three significant figures, tracking values from 1 microsecond to 1 hour with zero garbage collection overhead in the recording path.
- Coordinated Omission compensation: applies Gil Tene correction algorithms to account for client-side queue stalls during server hiccups.
- HTTP/2 and HTTP/1.1 client reuse: persistent connection pooling, multiplexing, and automatic fallback.
- WebSocket benchmarking engine: test streaming endpoints and stateful socket connections (`loadpulse ws`) with handshake and round-trip latency tracking.
- Live ANSI terminal dashboard: 10 FPS refresh displaying an RPS speedometer, microsecond latency percentile matrix, dynamic Unicode histogram bars, and color-coded status badges.
- Headless and CI detection: automatically switches to clean periodic log lines when running in non-interactive terminals or GitHub Actions.
- Declarative YAML scenarios: multi-step workflows with dynamic variables (`{{ uuid() }}`, `{{ random_int(1, 100) }}`, `{{ timestamp() }}`), response extraction (`$.data.token`), and external CSV data feeds.
- cURL importer: immediately convert production cURL commands into runnable benchmarks or exported YAML scenario files (`loadpulse from-curl`).
- Performance regression diff engine: compare baseline vs candidate benchmark runs (`loadpulse diff`) with customizable degradation thresholds.
- Automated SLA assertions: evaluate criteria like `--assert "p99 < 50ms && error_rate < 0.01"` and exit with status code 0 on success or 1 on SLA failure.
- Standalone HTML reports: self-contained single-file dark mode reports with inline SVG percentile ladder curves and distribution charts without CDN dependencies.
- Built-in mock benchmark target: includes an embedded high-throughput mock server with configurable delay, jitter, and error injection for self-testing and pipeline validation.

## Quickstart

### Prerequisites

- Java 21 LTS or newer
- Apache Maven 3.9+

### Build executable JAR

```bash
git clone https://github.com/alexandrmotologa/loadpulse.git
cd loadpulse
mvn clean package
```

The build creates a runnable fat JAR at `target/loadpulse.jar`.

### Basic run command

Benchmark an endpoint with 100 concurrent virtual threads for 30 seconds:

```bash
java -jar target/loadpulse.jar run http://localhost:8080/api/v1/orders \
  -c 100 \
  -d 30s \
  --warmup 3s
```

### Staged ramp-up traffic

Ramp up from 10 to 500 workers across staged intervals:

```bash
java -jar target/loadpulse.jar run http://localhost:8080/api/v1/checkout \
  --stages "15s:50,1m:200,30s:500,15s:0"
```

### Fixed rate (open workload model)

Generate a steady pace of 5,000 requests per second across 200 workers:

```bash
java -jar target/loadpulse.jar run http://localhost:8080/api/v1/search \
  -c 200 \
  -r 5000 \
  -d 1m
```

### POST request with dynamic body and custom headers

```bash
java -jar target/loadpulse.jar run http://localhost:8080/api/v1/orders \
  -m POST \
  -c 50 \
  -d 20s \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: {{ uuid() }}" \
  -b '{"orderId": "{{ uuid() }}", "customerId": "CUST-{{ random_int(1, 500) }}", "amount": {{ random_int(10, 1000) }}}'
```

### Import and benchmark from a cURL command

Turn an intercepted browser or Postman cURL command directly into a 100-worker load test:

```bash
java -jar target/loadpulse.jar from-curl \
  "curl -X POST 'http://localhost:8080/api/login' -H 'Content-Type: application/json' -d '{\"user\":\"alice\"}'" \
  -c 100 -d 30s
```

Or export it directly to a clean YAML scenario file:

```bash
java -jar target/loadpulse.jar from-curl \
  "curl -X GET 'http://localhost:8080/api/catalog'" \
  --yaml scenario-generated.yaml
```

### Compare benchmark runs (Regression Diff)

Evaluate candidate performance against a previous baseline in CI:

```bash
java -jar target/loadpulse.jar diff baseline.json candidate.json --threshold 10% --html diff-report.html
```

### WebSocket benchmarking

Benchmark streaming WebSocket servers with 50 concurrent connections:

```bash
java -jar target/loadpulse.jar ws ws://localhost:8080/ws/echo \
  -c 50 \
  -d 30s \
  -m '{"action":"ping"}' \
  --expect '{"action":"pong"}'
```

### Export HTML and JSON reports with SLA assertion

```bash
java -jar target/loadpulse.jar run http://localhost:8080/api/v1/orders \
  -c 50 \
  -d 15s \
  --assert "p99 < 50ms && error_rate < 0.01" \
  --html report.html \
  --json report.json
```

## Architecture

```
com.engine.loadpulse/
├── domain/                          # Zero-dependency domain models
│   ├── model/                       # BenchmarkConfig, LoadStage, HttpMethod, WorkloadModel
│   ├── metric/                      # LatencySample, PercentileSnapshot, HttpStatusSummary
│   └── scenario/                    # ScenarioDefinition, ScenarioStep, DataFeed, ResponseExtractor
├── engine/                          # Virtual Thread Engine Core
│   ├── WorkerPool.java              # Virtual thread executor managing concurrent workers & stages
│   ├── HttpClientPool.java          # Low-latency HTTP/1.1 & HTTP/2 connection reuse
│   ├── RateLimiter.java             # Token bucket rate pacer (System.nanoTime precision)
│   ├── ScenarioExecutor.java        # Multi-step sequential or burst scenario runner
│   └── websocket/                   # WebSocket benchmark engine with round-trip tracking
├── histogram/                       # Microsecond-Precision Latency Telemetry
│   ├── LatencyRecorder.java         # Thread-safe HdrHistogram accumulator
│   └── RollingWindowHistogram.java  # Sliding 1-second snapshots for live TUI graphs
├── tui/                             # High-FPS ANSI Terminal Visualization
│   ├── TerminalHud.java             # Full-screen dashboard and CI log switcher
│   ├── TerminalKeyboardListener.java# Interactive hotkeys (+, -, p, q)
│   ├── AsciiHistogramWidget.java    # Dynamic Unicode histogram distribution bars
│   ├── SpeedometerWidget.java       # Current RPS vs Peak RPS gauge
│   └── ErrorSummaryWidget.java      # Color-coded status and error breakdowns
├── report/                          # Exporters & SLA Evaluation
│   ├── HtmlReportGenerator.java     # Self-contained offline HTML report with SVG charts
│   ├── JsonReportGenerator.java     # Machine-readable JSON summary for CI/CD
│   ├── DiffReportGenerator.java     # Baseline vs candidate regression diff reporter
│   └── SlaAssertionEvaluator.java   # Rule evaluator for p99, p95, rps, and error rate
├── mock/                            # Built-in Mock Server
│   └── MockBenchmarkServer.java     # Embedded Virtual Thread HTTP server with delay and jitter
└── cli/                             # PicoCLI Commands
    ├── LoadPulseCli.java            # Root command & help metadata
    ├── RunCommand.java              # CLI runner (with stages, ramp-up, hotkeys)
    ├── ScenarioCommand.java         # YAML scenario runner (with feeds & response extraction)
    ├── FromCurlCommand.java         # cURL command importer & YAML generator
    ├── DiffCommand.java             # Performance regression diff checker
    ├── WebSocketCommand.java        # WebSocket load test command
    └── MockServerCommand.java       # Mock server runner
```

## Terminal HUD

When run in an interactive terminal, LoadPulse clears the screen and renders a 10 FPS dashboard:

```text
LOADPULSE ─ High-Throughput Reactive Benchmark Engine
Target: http://localhost:8080/bench │ Workers: 50 (Virtual Threads) │ Time: 00:12 / 00:30 (18s left)
──────────────────────────────────────────────────────────────────────────────
Throughput:
  [━━━━━━━━━━━━━━━━━━━━────]    4,820 req/s │ Peak:    5,120 req/s │   1.48 Mbps

Latency Percentiles (Microsecond Precision):
  Min        p50        p75        p90        p95        p99        p99.9      Max
  1.21ms     3.45ms     4.82ms     7.10ms     9.80ms     14.20ms    22.40ms    31.50ms

Distribution Histogram:
  0-1ms                                       0 (  0.0%)
  1-5ms      █████████████████████████    38200 ( 76.4%)
  5-15ms     ██████                        9840 ( 19.7%)
  15-50ms    █                             1960 (  3.9%)
  50-100ms                                    0 (  0.0%)

Status:
  2xx: 50,000  3xx: 0  4xx: 0  5xx: 0  Timeouts: 0  ConnErrors: 0  (Error Rate: 0.00%)
──────────────────────────────────────────────────────────────────────────────
```

In CI environments (e.g. GitHub Actions) or when `--no-tui` is passed, LoadPulse outputs single-line status updates every second to avoid terminal control code noise.

## YAML Scenarios

Complex multi-step user workflows can be declared in YAML files:

```yaml
name: "Checkout workflow load test"
target: "http://localhost:8080"
concurrency: 50
duration: "30s"
warmup: "5s"
timeout: "5s"
steps:
  - name: "Health check"
    path: "/health"
    method: "GET"
    thinkTimeMs: 20

  - name: "Submit order"
    path: "/api/v1/orders"
    method: "POST"
    headers:
      Content-Type: "application/json"
      X-Idempotency-Key: "{{ uuid() }}"
    body: |
      {
        "id": "{{ uuid() }}",
        "customerId": "CUST-{{ random_int(1, 1000) }}",
        "amount": {{ random_int(10, 500) }},
        "timestamp": {{ timestamp() }},
        "batch": "{{ counter() }}"
      }
    thinkTimeMs: 50

assertion: "p99 < 50ms && error_rate < 0.01"
```

Run the scenario with:

```bash
java -jar target/loadpulse.jar scenario examples/order-load.yaml --html report.html
```

### Supported dynamic variables

| Variable | Description | Example Output |
| :--- | :--- | :--- |
| `{{ uuid() }}` | Random v4 UUID | `e2a488e0-3f41-4773-8941-26c7104b9015` |
| `{{ timestamp() }}` | Current epoch time in milliseconds | `1773318250123` |
| `{{ iso_timestamp() }}` | ISO-8601 UTC timestamp | `2026-09-11T11:32:00Z` |
| `{{ counter() }}` | Monotonically increasing sequence | `1`, `2`, `3` |
| `{{ random_int(min, max) }}` | Random integer between min and max | `42` |
| `{{ random_string(length) }}`| Random alphanumeric string | `aB8fK1z9` |

## CI/CD SLA Assertions

LoadPulse allows gating automated delivery pipelines based on performance metrics. If any rule is violated, the CLI returns exit code `1`:

```bash
java -jar target/loadpulse.jar run http://staging.internal:8080/api/v1/ping \
  -c 100 \
  -d 20s \
  --no-tui \
  --assert "p99 < 30ms && p95 < 15ms && error_rate < 0.005 && rps >= 2000"
```

Metrics supported in assertions include: `p50`, `p75`, `p90`, `p95`, `p99`, `p999`, `max`, `min`, `mean`, `rps`, `error_rate`, and `total_requests`.

## HTML Reports

LoadPulse generates standalone, dark-themed HTML benchmark reports (`--html report.html`) containing:
- Executive summary metrics cards (Throughput, p99 Latency, Success Rate, Bandwidth)
- Interactive SVG Percentile Ladder Curve (logarithmic progression from p0 to p99.99)
- Latency Distribution bar chart
- Percentile detail table (Min, p50, p75, p90, p95, p99, p99.9, p99.99, Max)
- Status code and error breakdown
- SLA evaluation result badge

The HTML files contain inline styles and vector graphics, making them suitable for artifact archiving in CI pipelines without external network access.

## Embedded Mock Server

For testing environments without an existing API server, LoadPulse provides a built-in mock server powered by Virtual Threads:

```bash
# Start mock server on port 8080 with 5ms simulated delay and 2ms jitter
java -jar target/loadpulse.jar mock -p 8080 --delay 5ms --jitter 2ms
```

To simulate unstable networks or server faults, add error injection:

```bash
java -jar target/loadpulse.jar mock -p 8080 --delay 10ms --error-rate 0.02
```

## Verification and testing

Run the full test suite with Maven:

```bash
mvn clean test
```

The test suite includes:
- `LatencyRecorderTest`: verifies microsecond latency precision and Coordinated Omission compensation.
- `DynamicPayloadGeneratorTest`: tests dynamic variable interpolation.
- `RateLimiterTest`: validates token bucket throughput pacing accuracy.
- `WorkerPoolIntegrationTest`: validates 100+ concurrent Virtual Threads against an in-memory mock server.
- `ScenarioExecutorTest`: runs multi-step YAML test workflows.
- `SlaAssertionEvaluatorTest`: tests SLA rule parsing and threshold violations.
- `HtmlReportGeneratorTest`: validates standalone HTML report generation.

## License

MIT License. Copyright (c) 2026 Alexandr Motologa.
