package com.notebook.lumen.notification.policy.domain;

import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "workspace_notification_policies",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_workspace_notification_policy",
            columnNames = {"workspace_id", "notification_type", "channel"}))
public class WorkspaceNotificationPolicy {

  @Id private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "notification_type", nullable = false, length = 100)
  private UserNotificationType notificationType;

  @Enumerated(EnumType.STRING)
  @Column(name = "channel", nullable = false, length = 50)
  private NotificationChannel channel;

  @Enumerated(EnumType.STRING)
  @Column(name = "policy_mode", nullable = false, length = 50)
  private WorkspaceNotificationPolicyMode policyMode;

  @Column(name = "reason")
  private String reason;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(name = "updated_by_user_id")
  private UUID updatedByUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected WorkspaceNotificationPolicy() {}

  public WorkspaceNotificationPolicy(
      UUID id,
      UUID workspaceId,
      UserNotificationType notificationType,
      NotificationChannel channel,
      WorkspaceNotificationPolicyMode policyMode,
      String reason,
      UUID createdByUserId,
      UUID updatedByUserId,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.workspaceId = workspaceId;
    this.notificationType = notificationType;
    this.channel = channel;
    this.policyMode = policyMode;
    this.reason = reason;
    this.createdByUserId = createdByUserId;
    this.updatedByUserId = updatedByUserId;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public void updatePolicy(
      WorkspaceNotificationPolicyMode policyMode,
      String reason,
      UUID updatedByUserId,
      Instant now) {
    this.policyMode = policyMode;
    this.reason = reason;
    this.updatedByUserId = updatedByUserId;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
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

  public WorkspaceNotificationPolicyMode getPolicyMode() {
    return policyMode;
  }

  public String getReason() {
    return reason;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public UUID getUpdatedByUserId() {
    return updatedByUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
