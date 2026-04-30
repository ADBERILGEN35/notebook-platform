# Observability

## Optional Local Stack

Prometheus and a basic OpenTelemetry Collector can be started without changing the main compose file:

```bash
docker compose -f docker-compose.yml -f docker-compose.observability.yml --profile observability up --build
```

Prometheus listens on `http://localhost:9090`. The collector exposes OTLP HTTP on `http://localhost:4318`.

## Logging

All services write console logs as JSON through Logback. Each record includes at least:

- `@timestamp`
- `level`
- `service`
- `logger_name`
- `message`
- MDC values when present: `requestId`, `userId`, `workspaceId`, `method`, `path`, `status`, `durationMs`, `routeId`
- stack traces for logged exceptions

Servlet services use a request logging filter. The gateway uses WebFlux filters; request access logs include route, method, path, status and duration. Reactor MDC propagation is intentionally limited to request/access log points and error paths; deeper asynchronous operator-level MDC propagation is left for later hardening.

## Request ID

`X-Request-Id` is the standard correlation header.

- Gateway accepts a valid UUID from the client or creates a new UUID.
- Downstream services read `X-Request-Id`; if absent or invalid, they create a local UUID.
- Services add `X-Request-Id` to responses.
- Error responses include `requestId`.

## Actuator And Prometheus

Standard endpoints:

- `/actuator/health`
- `/actuator/health/liveness`
- `/actuator/health/readiness`
- `/actuator/metrics`
- `/actuator/prometheus`

Production deployments should protect actuator endpoints at the edge or network layer.
Prod profile defaults are intentionally narrower and should be overridden only for private scrape networks:

```bash
MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,prometheus
```

## OpenTelemetry

Tracing is disabled by default:

```bash
OTEL_ENABLED=false
```

Enable OTLP HTTP export:

```bash
export OTEL_ENABLED=true
export OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces
export OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
export MANAGEMENT_TRACING_SAMPLING_PROBABILITY=1.0
```

For production, use a lower sampling probability and a collector endpoint managed by the platform.
