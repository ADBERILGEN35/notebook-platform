package com.notebook.lumen.notification.preference.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "user_notification_delivery_preferences")
public class UserNotificationDeliveryPreference {
  @Id private UUID id;

  @Column(name = "user_id", nullable = false, unique = true)
  private UUID userId;

  @Column(name = "email_digest_enabled", nullable = false)
  private boolean emailDigestEnabled;

  @Enumerated(EnumType.STRING)
  @Column(name = "email_digest_frequency", nullable = false)
  private EmailDigestFrequency emailDigestFrequency;

  @Column(name = "quiet_hours_enabled", nullable = false)
  private boolean quietHoursEnabled;

  @Column(name = "quiet_hours_start")
  private LocalTime quietHoursStart;

  @Column(name = "quiet_hours_end")
  private LocalTime quietHoursEnd;

  @Column(name = "timezone", nullable = false)
  private String timezone;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected UserNotificationDeliveryPreference() {}

  public UserNotificationDeliveryPreference(UUID id, UUID userId, Instant now) {
    this.id = id;
    this.userId = userId;
    this.emailDigestEnabled = false;
    this.emailDigestFrequency = EmailDigestFrequency.DAILY;
    this.quietHoursEnabled = false;
    this.timezone = "UTC";
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void update(
      boolean emailDigestEnabled,
      EmailDigestFrequency emailDigestFrequency,
      boolean quietHoursEnabled,
      LocalTime quietHoursStart,
      LocalTime quietHoursEnd,
      String timezone,
      Instant now) {
    this.emailDigestEnabled = emailDigestEnabled;
    this.emailDigestFrequency = emailDigestFrequency;
    this.quietHoursEnabled = quietHoursEnabled;
    this.quietHoursStart = quietHoursStart;
    this.quietHoursEnd = quietHoursEnd;
    this.timezone = timezone;
    this.updatedAt = now;
  }

  public UUID getUserId() {
    return userId;
  }

  public boolean isEmailDigestEnabled() {
    return emailDigestEnabled;
  }

  public EmailDigestFrequency getEmailDigestFrequency() {
    return emailDigestFrequency;
  }

  public boolean isQuietHoursEnabled() {
    return quietHoursEnabled;
  }

  public LocalTime getQuietHoursStart() {
    return quietHoursStart;
  }

  public LocalTime getQuietHoursEnd() {
    return quietHoursEnd;
  }

  public String getTimezone() {
    return timezone;
  }
}
