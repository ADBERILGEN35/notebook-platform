package com.notebook.lumen.notification.analytics;

/** Low-cardinality analytics event kinds (aggregate table). */
public enum NotificationAnalyticsEventKind {
  CREATED,
  QUEUED,
  SENT,
  FAILED,
  DEAD,
  SKIPPED_PREFERENCE,
  SKIPPED_WORKSPACE_PREFERENCE,
  /** Delivery skipped because workspace admin policy force-disabled the channel. */
  SKIPPED_WORKSPACE_ADMIN_POLICY,
  DIGEST_QUEUED,
  DIGEST_SENT,
  QUIET_HOURS_DELAYED,
  FANOUT_PENDING,
  FANOUT_SENT,
  FANOUT_DEAD,
  /** SSE delivery failure to a browser connection (hourly aggregate). */
  SSE_SEND_FAILURE,
  /** Redis pub/sub publish attempts. */
  REDIS_FANOUT_PUBLISH_SUCCESS,
  REDIS_FANOUT_PUBLISH_FAILURE,
  /** Messages handled by local Redis subscriber (best-effort aggregate). */
  REDIS_FANOUT_SUBSCRIBER_RECEIVED
}
