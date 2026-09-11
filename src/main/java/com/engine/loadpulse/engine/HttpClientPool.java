package com.engine.loadpulse.engine;

import com.engine.loadpulse.domain.model.HttpMethod;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpClientPool implements AutoCloseable {
    private final HttpClient httpClient;
    private final ExecutorService executor;

    public HttpClientPool(boolean useHttp2, Duration connectTimeout) {
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        HttpClient.Builder builder = HttpClient.newBuilder()
                .executor(this.executor)
                .connectTimeout(connectTimeout != null ? connectTimeout : Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .version(useHttp2 ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1);

        this.httpClient = builder.build();
    }

    public static HttpRequest createRequest(
            URI uri,
            HttpMethod method,
            Map<String, String> headers,
            byte[] body,
            Duration timeout
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri);

        if (timeout != null && !timeout.isZero()) {
            builder.timeout(timeout);
        }

        if (headers != null) {
            headers.forEach(builder::header);
        }

        HttpRequest.BodyPublisher publisher = (body != null && body.length > 0)
                ? HttpRequest.BodyPublishers.ofByteArray(body)
                : HttpRequest.BodyPublishers.noBody();

        switch (method) {
            case GET -> builder.GET();
            case POST -> builder.POST(publisher);
            case PUT -> builder.PUT(publisher);
            case DELETE -> builder.DELETE();
            case PATCH -> builder.method("PATCH", publisher);
            case HEAD -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
        }

        return builder.build();
    }

    public HttpResponse<byte[]> send(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    public HttpClient getUnderlyingClient() {
        return httpClient;
    }

    @Override
    public void close() {
        executor.close();
    }
}
