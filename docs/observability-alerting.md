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
- search indexing outbox failed count > 0 for sustained periods
- search indexing retry spike or oldest pending age high
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

Faz 32 notification-service exposes provider lifecycle metrics:
`email_provider_send_total{provider,status}`, `email_provider_send_failure_total{provider}`,
`email_webhook_events_total{provider,eventType}`,
`email_webhook_signature_failures_total{provider}`, `email_bounces_total{provider}`,
`email_complaints_total{provider}` and `email_suppressed_total{reason}`. Alert on provider send
failure spikes, webhook signature failures, bounce/complaint rate spikes and suppression spikes.

Faz 26 content-service exposes search outbox metrics:
`search_outbox_pending`, `search_outbox_failed`, `search_outbox_processed_total`,
`search_outbox_failed_total`, `search_outbox_retry_total`,
`search_outbox_processing_duration` and `search_outbox_oldest_pending_age`.

Faz 31 search-service exposes provider metrics:
`search_provider_requests_total{provider,operation}`,
`search_provider_failures_total{provider,operation}`,
`search_provider_latency{provider,operation}`, `search_provider_fallback_total{operation}`,
`opensearch_index_upserts_total` and `opensearch_search_queries_total`. Alert on OpenSearch
failure spikes, fallback spikes, upsert failures and high search p95 latency.

Alert on failed outbox count, oldest pending age, retry spikes and search-service 5xx/unavailable
symptoms. Query text and note body should not be logged or stored in audit metadata.

Faz 27 search reindex metrics include `search_reindex_jobs_total`, `search_reindex_running`,
`search_reindex_completed_total`, `search_reindex_failed_total`, `search_reindex_scanned_total`,
`search_reindex_indexed_total`, `search_reindex_duration` and
`search_reindex_last_run_timestamp`. Alert on failed jobs, jobs running too long and repeated
content-source API failures.

Faz 28 cleanup metrics include `search_reindex_orphans_archived_total`,
`search_reindex_cleanup_duration` and `search_reindex_cleanup_skipped_total`. Alert if archived
orphan count is unexpectedly high for the requested scope or if cleanup fails after a successful
scan.

Faz 29 dry-run metrics include `search_reindex_cleanup_dry_run_total`,
`search_reindex_cleanup_preview_count` and `search_reindex_orphan_preview_viewed_total`. Before
real cleanup rollout, run a scoped dry-run and compare the preview count with the expected orphan
range. Alert or block promotion when dry-run orphan count is unexpectedly high. A future alert can
compare real cleanup archived count against the most recent dry-run count for the same scope.

Faz 30 worker coordination metrics include `email_worker_claimed_total`,
`email_worker_stale_recovered_total`, `email_worker_lock_expired_total`,
`search_outbox_claimed_total`, `search_outbox_stale_recovered_total`,
`search_outbox_lock_expired_total`, `search_reindex_claimed_total`,
`search_reindex_stale_failed_total`, `search_reindex_lock_expired_total` and
`search_reindex_heartbeat_timestamp`. Alert when stuck `SENDING` or `PROCESSING` counts rise,
stale recovery spikes, reindex jobs run too long, or queue depth grows while claimed counters remain
flat.

## GitOps Promotion Usage

Staging and prod GitOps values enable `serviceMonitor.enabled=true` so Prometheus Operator based
clusters can scrape the actuator Prometheus endpoints after sync. Dev keeps it disabled by default.

Before production promotion, import the Grafana dashboards, load the Prometheus rules and run the
post-sync health/smoke checks from `docs/gitops-deployment.md`.
