# Observability Dashboards and Alerting

Faz 17 adds starter Grafana dashboards and Prometheus alert rules for the existing actuator
Prometheus endpoints. It does not install Prometheus, Grafana or Alertmanager.

## Files

- `observability/grafana/dashboards/notebook-platform-overview.json`
- `observability/grafana/dashboards/api-gateway.json`
- `observability/grafana/dashboards/services-jvm.json`
- `observability/grafana/dashboards/resilience4j.json`
- `observability/prometheus/alerts/notebook-platform-alerts.yml`

## Dashboard Coverage

- HTTP request rate by service and route.
- HTTP p95/p99 latency.
- 4xx/5xx rate.
- api-gateway 429 rate-limit spikes.
- Service availability through `up`.
- JVM heap usage.
- JVM GC pause p95.
- Hikari active and max DB pool connections.
- Resilience4j circuit breaker state, calls and retry calls.
- Best-effort error code cardinality panel for future explicit `errorCode` labels.

## Alert Coverage

- service down
- high 5xx rate
- high p95 latency
- circuit breaker open
- DB pool saturation
- high JVM memory usage
- api-gateway 429 spike
- workspace-service unavailable symptoms from content-service 503s
- missing or invalid internal auth spike on `/internal/**`
- RLS rollout strict-header 400 spike, approximated by service/status metrics and confirmed through
  logs for `MISSING_WORKSPACE_CONTEXT` and `INVALID_WORKSPACE_CONTEXT`
- RLS rollout 403 spike
- RLS rollout 5xx DB permission symptoms
- notification-service email send failure spike
- notification-service queued email backlog growth
- search-service down
- search indexing failure spike
- search query p95 latency high
- search permission client failures

Thresholds are initial production-readiness defaults. They should be tuned after baseline traffic
and k6 profiles are available.

## Import Notes

Grafana dashboards assume a Prometheus datasource variable named `${DS_PROMETHEUS}`. If the target
Grafana instance uses another datasource UID, update the dashboard datasource mappings during import.

Prometheus alert rules can be loaded by a Prometheus rule file mount or by a Prometheus Operator
`PrometheusRule` wrapper in a later deployment phase.

During Runtime RLS rollout, current metrics do not expose `errorCode` as a Prometheus label. Use the
status-based starter alerts plus log queries for:

- `MISSING_WORKSPACE_CONTEXT`
- `INVALID_WORKSPACE_CONTEXT`
- `permission denied`
- `violates row-level security policy`
- `app.current_workspace_id`

Adding explicit error-code metrics is future work.

Faz 24 notification-service logs queue transitions and exposes actuator Prometheus like the other
services. Custom notification counters such as `email_notifications_total` and queue-depth gauges
remain future hardening; until then, alert on service health, HTTP 5xx and logs containing
`EMAIL_NOTIFICATION_FAILED`.

Faz 25 search-service exposes actuator Prometheus and logs search/indexing events. Custom counters
such as `search_queries_total`, `search_index_upserts_total` and
`search_permission_filter_denied_total` remain future hardening; until then, alert on service health,
HTTP 5xx, latency and logs containing `SEARCH_INDEXING_FAILED`.

## GitOps Promotion Usage

Staging and prod GitOps values enable `serviceMonitor.enabled=true` so Prometheus Operator based
clusters can scrape the actuator Prometheus endpoints after sync. Dev keeps it disabled by default.

Before production promotion, import the Grafana dashboards, load the Prometheus rules and run the
post-sync health/smoke checks from `docs/gitops-deployment.md`.
