package com.notebook.lumen.notification.policy.infrastructure;

import com.notebook.lumen.notification.policy.domain.WorkspaceNotificationPolicy;
import com.notebook.lumen.notification.preference.domain.NotificationChannel;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceNotificationPolicyRepository
    extends JpaRepository<WorkspaceNotificationPolicy, UUID> {

  List<WorkspaceNotificationPolicy> findByWorkspaceId(UUID workspaceId);

  Optional<WorkspaceNotificationPolicy> findByWorkspaceIdAndNotificationTypeAndChannel(
      UUID workspaceId, UserNotificationType notificationType, NotificationChannel channel);

  void deleteByWorkspaceId(UUID workspaceId);
}
