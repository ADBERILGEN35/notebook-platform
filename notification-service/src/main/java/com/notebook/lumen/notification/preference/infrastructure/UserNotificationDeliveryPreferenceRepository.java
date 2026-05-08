package com.notebook.lumen.notification.preference.infrastructure;

import com.notebook.lumen.notification.preference.domain.UserNotificationDeliveryPreference;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationDeliveryPreferenceRepository
    extends JpaRepository<UserNotificationDeliveryPreference, UUID> {
  Optional<UserNotificationDeliveryPreference> findByUserId(UUID userId);
}
