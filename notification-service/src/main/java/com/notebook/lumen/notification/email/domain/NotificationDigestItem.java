package com.notebook.lumen.notification.email.domain;

import com.notebook.lumen.notification.user.domain.UserNotificationType;
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
@Table(name = "notification_digest_items")
public class NotificationDigestItem {
  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "recipient_email", nullable = false)
  private String recipientEmail;

  @Enumerated(EnumType.STRING)
  @Column(name = "notification_type", nullable = false)
  private UserNotificationType notificationType;

  @Column(name = "source_notification_id")
  private UUID sourceNotificationId;

  @Column(name = "email_notification_id")
  private UUID emailNotificationId;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "message", nullable = false, columnDefinition = "text")
  private String message;

  @Column(name = "action_url")
  private String actionUrl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
  private Map<String, Object> metadata;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private NotificationDigestItemStatus status;

  @Column(name = "scheduled_for", nullable = false)
  private Instant scheduledFor;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected NotificationDigestItem() {}

  public NotificationDigestItem(
      UUID id,
      UUID userId,
      String recipientEmail,
      UserNotificationType notificationType,
      UUID sourceNotificationId,
      String title,
      String message,
      String actionUrl,
      Map<String, Object> metadata,
      Instant scheduledFor,
      Instant createdAt) {
    this.id = id;
    this.userId = userId;
    this.recipientEmail = recipientEmail;
    this.notificationType = notificationType;
    this.sourceNotificationId = sourceNotificationId;
    this.title = title;
    this.message = message;
    this.actionUrl = actionUrl;
    this.metadata = metadata == null ? Map.of() : metadata;
    this.status = NotificationDigestItemStatus.PENDING;
    this.scheduledFor = scheduledFor;
    this.createdAt = createdAt;
  }

  public void markSent(UUID emailNotificationId, Instant now) {
    this.emailNotificationId = emailNotificationId;
    this.status = NotificationDigestItemStatus.SENT;
    this.sentAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UserNotificationType getNotificationType() {
    return notificationType;
  }

  public String getRecipientEmail() {
    return recipientEmail;
  }

  public String getTitle() {
    return title;
  }

  public String getMessage() {
    return message;
  }
}
