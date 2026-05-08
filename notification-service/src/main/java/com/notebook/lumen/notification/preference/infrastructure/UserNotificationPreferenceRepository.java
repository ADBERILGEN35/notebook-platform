package com.notebook.lumen.notification.preference.infrastructure;

import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.preference.domain.UserNotificationPreference;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationPreferenceRepository
    extends JpaRepository<UserNotificationPreference, UUID> {
  List<UserNotificationPreference> findByUserId(UUID userId);

  Optional<UserNotificationPreference> findByUserIdAndNotificationTypeAndChannel(
      UUID userId, UserNotificationType notificationType, NotificationChannel channel);
}
