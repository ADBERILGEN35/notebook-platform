package com.notebook.lumen.notification.analytics;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryAnalyticsHourlyRepository
    extends JpaRepository<NotificationDeliveryAnalyticsHourly, java.util.UUID> {

  @Query(
      """
      select a.eventKind as eventKind, sum(a.count) as total
      from NotificationDeliveryAnalyticsHourly a
      where a.bucketStart >= :from and a.bucketStart < :to
      group by a.eventKind
      """)
  List<EventKindTotalProjection> sumByEventKind(
      @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      """
      select a.channel as channel, a.eventKind as eventKind, sum(a.count) as total
      from NotificationDeliveryAnalyticsHourly a
      where a.bucketStart >= :from and a.bucketStart < :to and a.channel <> ''
      group by a.channel, a.eventKind
      """)
  List<ChannelEventKindTotalProjection> sumByChannelAndEventKind(
      @Param("from") Instant from, @Param("to") Instant to);

  @Query(
      """
      select a.notificationType as notificationType, a.eventKind as eventKind, sum(a.count) as total
      from NotificationDeliveryAnalyticsHourly a
      where a.bucketStart >= :from and a.bucketStart < :to and a.notificationType <> ''
      group by a.notificationType, a.eventKind
      """)
  List<TypeEventKindTotalProjection> sumByTypeAndEventKind(
      @Param("from") Instant from, @Param("to") Instant to);

  interface EventKindTotalProjection {
    NotificationAnalyticsEventKind getEventKind();

    long getTotal();
  }

  interface ChannelEventKindTotalProjection {
    String getChannel();

    NotificationAnalyticsEventKind getEventKind();

    long getTotal();
  }

  interface TypeEventKindTotalProjection {
    String getNotificationType();

    NotificationAnalyticsEventKind getEventKind();

    long getTotal();
  }
}
