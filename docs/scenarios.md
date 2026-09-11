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
- `{{ vars.<name> }}`: Chained value extracted from previous step response.
- `{{ feed.<column> }}`: Value from an external CSV data feed.

## Load stages and ramp-up

Instead of a constant concurrency, you can define stepped or ramping traffic patterns:

```yaml
name: "Staged spike test"
target: "http://localhost:8080"
stages:
  - duration: "15s"
    target: 20
  - duration: "30s"
    target: 100
  - duration: "15s"
    target: 0
steps:
  - name: "Health"
    path: "/health"
    method: "GET"
```

You can also specify stages directly on the CLI:
```bash
java -jar target/loadpulse.jar run http://localhost:8080/api/users --stages "15s:20,30s:100,15s:0"
```

## CSV data feeds

Feed external test records (e.g. user credentials or item IDs) into your benchmark:

```yaml
name: "Data-driven auth test"
target: "http://localhost:8080"
dataFeeds:
  users:
    file: "data/users.csv"
    strategy: "round_robin"  # or "random"
steps:
  - name: "Login"
    path: "/api/login"
    method: "POST"
    headers:
      Content-Type: "application/json"
    body: |
      {
        "username": "{{ feed.username }}",
        "password": "{{ feed.password }}"
      }
```

## Response extraction and variable chaining

Extract tokens, IDs, or session keys from HTTP response bodies (JSON Path) or headers and feed them into later requests:

```yaml
name: "Stateful user flow"
target: "http://localhost:8080"
steps:
  - name: "Authenticate"
    path: "/api/auth/token"
    method: "POST"
    body: '{"client_id": "test"}'
    extract:
      authToken: "$.data.access_token"
      requestId: "header:X-Request-Id"

  - name: "Get Protected Resource"
    path: "/api/account"
    method: "GET"
    headers:
      Authorization: "Bearer {{ vars.authToken }}"
      X-Correlation-Id: "{{ vars.requestId }}"
```

