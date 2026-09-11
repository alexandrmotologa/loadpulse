# YAML Scenario Guide

LoadPulse supports YAML-defined benchmark scenarios for complex user flows and multi-step API transactions.

## Basic scenario structure

A single endpoint scenario:

```yaml
name: "User profile benchmark"
target: "http://localhost:8080/api/v1/users"
method: "GET"
concurrency: 50
duration: "30s"
warmup: "5s"
rps: 2000
timeout: "5s"
headers:
  Accept: "application/json"
  Authorization: "Bearer token-123"
assertion: "p99 < 40ms && error_rate < 0.01"
```

## Multi-step workflow scenario

A sequential workflow simulated by each virtual thread:

```yaml
name: "E-Commerce checkout scenario"
target: "http://localhost:8080"
concurrency: 30
duration: "45s"
warmup: "5s"
timeout: "5s"
steps:
  - name: "Browse Catalog"
    path: "/api/products?category=electronics"
    method: "GET"
    thinkTimeMs: 50

  - name: "Add to Cart"
    path: "/api/cart"
    method: "POST"
    headers:
      Content-Type: "application/json"
    body: |
      {
        "cartId": "{{ uuid() }}",
        "productId": "PROD-{{ random_int(1, 200) }}",
        "quantity": {{ random_int(1, 3) }}
      }
    thinkTimeMs: 100

  - name: "Checkout"
    path: "/api/checkout"
    method: "POST"
    headers:
      Content-Type: "application/json"
      X-Idempotency-Key: "{{ uuid() }}"
    body: |
      {
        "paymentMethod": "CARD",
        "amount": {{ random_int(50, 500) }},
        "timestamp": {{ timestamp() }}
      }
    thinkTimeMs: 50

assertion: "p95 < 80ms && error_rate < 0.02"
```

## Dynamic template functions

LoadPulse interpolates dynamic variables in URLs, headers, and request bodies:

- `{{ uuid() }}`: Generates a new random UUID v4 string.
- `{{ timestamp() }}`: Inserts current Unix epoch milliseconds.
- `{{ iso_timestamp() }}`: Inserts ISO-8601 UTC timestamp string.
- `{{ counter() }}`: An atomic sequential number, incrementing with every request.
- `{{ random_int(min, max) }}`: Random integer between `min` and `max` (inclusive).
- `{{ random_string(length) }}`: Random alphanumeric string with the specified length.
