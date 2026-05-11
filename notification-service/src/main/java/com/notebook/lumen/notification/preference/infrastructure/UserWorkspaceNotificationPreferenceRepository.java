package com.notebook.lumen.notification.preference.infrastructure;

import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.preference.domain.UserWorkspaceNotificationPreference;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWorkspaceNotificationPreferenceRepository
    extends JpaRepository<UserWorkspaceNotificationPreference, UUID> {

  List<UserWorkspaceNotificationPreference> findByUserIdAndWorkspaceId(
      UUID userId, UUID workspaceId);

  Optional<UserWorkspaceNotificationPreference>
      findByUserIdAndWorkspaceIdAndNotificationTypeAndChannel(
          UUID userId,
          UUID workspaceId,
          UserNotificationType notificationType,
          NotificationChannel channel);

  void deleteByUserIdAndWorkspaceId(UUID userId, UUID workspaceId);
}
