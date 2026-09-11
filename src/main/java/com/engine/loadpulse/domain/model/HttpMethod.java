package com.engine.loadpulse.domain.model;

public enum HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH,
    HEAD;

    public static HttpMethod fromString(String method) {
        if (method == null || method.isBlank()) {
            return GET;
        }
        return HttpMethod.valueOf(method.trim().toUpperCase());
    }
}
