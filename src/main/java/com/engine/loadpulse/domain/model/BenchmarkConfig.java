package com.engine.loadpulse.domain.model;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record BenchmarkConfig(
        URI targetUri,
        HttpMethod method,
        int concurrency,
        Duration duration,
        Duration warmupDuration,
        int targetRps,
        Map<String, String> headers,
        String body,
        Duration requestTimeout,
        boolean http2,
        boolean noTui,
        Path jsonReportPath,
        Path htmlReportPath,
        String assertionExpression
) {
    public BenchmarkConfig {
        Objects.requireNonNull(targetUri, "targetUri cannot be null");
        Objects.requireNonNull(method, "method cannot be null");
        if (concurrency <= 0) {
            concurrency = 10;
        }
        if (duration == null || duration.isZero() || duration.isNegative()) {
            duration = Duration.ofSeconds(10);
        }
        if (warmupDuration == null || warmupDuration.isNegative()) {
            warmupDuration = Duration.ZERO;
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            requestTimeout = Duration.ofSeconds(10);
        }
        headers = headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
    }

    public WorkloadModel workloadModel() {
        return targetRps > 0 ? WorkloadModel.OPEN_RATE_PACED : WorkloadModel.CLOSED_CONCURRENCY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private URI targetUri;
        private HttpMethod method = HttpMethod.GET;
        private int concurrency = 50;
        private Duration duration = Duration.ofSeconds(10);
        private Duration warmupDuration = Duration.ZERO;
        private int targetRps = 0;
        private Map<String, String> headers = Collections.emptyMap();
        private String body;
        private Duration requestTimeout = Duration.ofSeconds(10);
        private boolean http2 = true;
        private boolean noTui = false;
        private Path jsonReportPath;
        private Path htmlReportPath;
        private String assertionExpression;

        public Builder targetUri(URI targetUri) {
            this.targetUri = targetUri;
            return this;
        }

        public Builder method(HttpMethod method) {
            this.method = method;
            return this;
        }

        public Builder concurrency(int concurrency) {
            this.concurrency = concurrency;
            return this;
        }

        public Builder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public Builder warmupDuration(Duration warmupDuration) {
            this.warmupDuration = warmupDuration;
            return this;
        }

        public Builder targetRps(int targetRps) {
            this.targetRps = targetRps;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        public Builder http2(boolean http2) {
            this.http2 = http2;
            return this;
        }

        public Builder noTui(boolean noTui) {
            this.noTui = noTui;
            return this;
        }

        public Builder jsonReportPath(Path jsonReportPath) {
            this.jsonReportPath = jsonReportPath;
            return this;
        }

        public Builder htmlReportPath(Path htmlReportPath) {
            this.htmlReportPath = htmlReportPath;
            return this;
        }

        public Builder assertionExpression(String assertionExpression) {
            this.assertionExpression = assertionExpression;
            return this;
        }

        public BenchmarkConfig build() {
            return new BenchmarkConfig(
                    targetUri,
                    method,
                    concurrency,
                    duration,
                    warmupDuration,
                    targetRps,
                    headers,
                    body,
                    requestTimeout,
                    http2,
                    noTui,
                    jsonReportPath,
                    htmlReportPath,
                    assertionExpression
            );
        }
    }
}
