package com.notebook.lumen.notification.preference.domain;

import com.notebook.lumen.notification.user.domain.UserNotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_workspace_notification_preferences")
public class UserWorkspaceNotificationPreference {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "notification_type", nullable = false, length = 100)
  private UserNotificationType notificationType;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, length = 50)
  private NotificationChannel channel;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserWorkspaceNotificationPreference() {}

  public UserWorkspaceNotificationPreference(
      UUID id,
      UUID userId,
      UUID workspaceId,
      UserNotificationType notificationType,
      NotificationChannel channel,
      boolean enabled,
      Instant now) {
    this.id = id;
    this.userId = userId;
    this.workspaceId = workspaceId;
    this.notificationType = notificationType;
    this.channel = channel;
    this.enabled = enabled;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void updateEnabled(boolean enabled, Instant now) {
    this.enabled = enabled;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UserNotificationType getNotificationType() {
    return notificationType;
  }

  public NotificationChannel getChannel() {
    return channel;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
