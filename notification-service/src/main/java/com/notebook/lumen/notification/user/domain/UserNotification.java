package com.notebook.lumen.notification.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_notifications")
public class UserNotification {
  @Id private UUID id;

  @Column(name = "recipient_user_id", nullable = false)
  private UUID recipientUserId;

  @Column(name = "workspace_id")
  private UUID workspaceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private UserNotificationType type;

  @Column(name = "title", nullable = false, length = 200)
  private String title;

  @Column(name = "message", nullable = false, columnDefinition = "text")
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(name = "severity", nullable = false)
  private UserNotificationSeverity severity;

  @Column(name = "action_url", length = 500)
  private String actionUrl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  @Column(name = "idempotency_key")
  private String idempotencyKey;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserNotification() {}

  public UserNotification(
      UUID id,
      UUID recipientUserId,
      UUID workspaceId,
      UserNotificationType type,
      String title,
      String message,
      UserNotificationSeverity severity,
      String actionUrl,
      Map<String, Object> metadata,
      String idempotencyKey,
      Instant now) {
    this.id = id;
    this.recipientUserId = recipientUserId;
    this.workspaceId = workspaceId;
    this.type = type;
    this.title = title;
    this.message = message;
    this.severity = severity;
    this.actionUrl = actionUrl;
    this.metadata = metadata;
    this.idempotencyKey = idempotencyKey;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void markRead(Instant now) {
    if (readAt == null) {
      readAt = now;
    }
    updatedAt = now;
  }

  public void archive(Instant now) {
    archivedAt = now;
    updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRecipientUserId() {
    return recipientUserId;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UserNotificationType getType() {
    return type;
  }

  public String getTitle() {
    return title;
  }

  public String getMessage() {
    return message;
  }

  public UserNotificationSeverity getSeverity() {
    return severity;
  }

  public String getActionUrl() {
    return actionUrl;
  }

  public Map<String, Object> getMetadata() {
    return metadata;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
