package com.notebook.lumen.notification.user.infrastructure;

import com.notebook.lumen.notification.user.domain.UserNotification;
import com.notebook.lumen.notification.user.domain.UserNotificationType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {
  Optional<UserNotification> findByRecipientUserIdAndIdempotencyKey(
      UUID recipientUserId, String idempotencyKey);

  @Query(
      """
      select n
      from UserNotification n
      where n.recipientUserId = :recipientUserId
        and n.archivedAt is null
        and (:unreadOnly = false or n.readAt is null)
        and (:type is null or n.type = :type)
        and (:workspaceId is null or n.workspaceId = :workspaceId)
      """)
  Page<UserNotification> findVisibleForRecipient(
      @Param("recipientUserId") UUID recipientUserId,
      @Param("unreadOnly") boolean unreadOnly,
      @Param("type") UserNotificationType type,
      @Param("workspaceId") UUID workspaceId,
      Pageable pageable);

  @Query(
      """
      select count(n)
      from UserNotification n
      where n.recipientUserId = :recipientUserId
        and n.archivedAt is null
        and n.readAt is null
      """)
  long countUnread(@Param("recipientUserId") UUID recipientUserId);

  @Query(
      """
      select n
      from UserNotification n
      where n.id = :id
        and n.recipientUserId = :recipientUserId
        and n.archivedAt is null
      """)
  Optional<UserNotification> findVisibleOwned(
      @Param("id") UUID id, @Param("recipientUserId") UUID recipientUserId);

  @Query(
      """
      select n
      from UserNotification n
      where n.recipientUserId = :recipientUserId
        and n.archivedAt is null
        and (:workspaceId is null or n.workspaceId = :workspaceId)
      """)
  java.util.List<UserNotification> findVisibleOwnedForReadAll(
      @Param("recipientUserId") UUID recipientUserId, @Param("workspaceId") UUID workspaceId);
}
