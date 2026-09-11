package com.engine.loadpulse.domain.scenario;

import com.engine.loadpulse.domain.model.HttpMethod;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScenarioStep(
        String name,
        String path,
        String method,
        Map<String, String> headers,
        Map<String, String> queryParams,
        String body,
        long thinkTimeMs,
        Map<String, String> extract
) {
    public ScenarioStep {
        if (method == null || method.isBlank()) {
            method = "GET";
        }
        headers = headers != null ? Collections.unmodifiableMap(headers) : Collections.emptyMap();
        queryParams = queryParams != null ? Collections.unmodifiableMap(queryParams) : Collections.emptyMap();
        extract = extract != null ? Collections.unmodifiableMap(extract) : Collections.emptyMap();
    }

    public HttpMethod resolvedMethod() {
        return HttpMethod.fromString(method);
    }
}
