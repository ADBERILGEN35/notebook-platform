package com.notebook.lumen.content.search.outbox.application;

import com.notebook.lumen.content.client.search.SearchClient;
import com.notebook.lumen.content.config.ContentProperties;
import com.notebook.lumen.content.search.outbox.SearchIndexOutboxEvent;
import com.notebook.lumen.content.search.outbox.SearchIndexOutboxEventType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SearchIndexOutboxWorker {
  private static final Logger log = LoggerFactory.getLogger(SearchIndexOutboxWorker.class);

  private final SearchIndexOutboxService outboxService;
  private final SearchClient searchClient;
  private final ContentProperties properties;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;
  private final AtomicBoolean acceptingClaims = new AtomicBoolean(true);

  public SearchIndexOutboxWorker(
      SearchIndexOutboxService outboxService,
      SearchClient searchClient,
      ContentProperties properties,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry) {
    this.outboxService = outboxService;
    this.searchClient = searchClient;
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
  }

  @Scheduled(fixedDelayString = "#{@searchIndexOutboxWorker.pollIntervalMillis()}")
  public void poll() {
    if (!acceptingClaims.get() || !workerEnabled()) {
      return;
    }
    List<SearchIndexOutboxEvent> events = outboxService.claimDueEvents();
    for (SearchIndexOutboxEvent event : events) {
      process(event);
    }
  }

  public String pollIntervalMillis() {
    return String.valueOf(outbox().effectivePollIntervalSeconds() * 1000);
  }

  @jakarta.annotation.PreDestroy
  void stopAcceptingClaims() {
    acceptingClaims.set(false);
  }

  private void process(SearchIndexOutboxEvent event) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      if (SearchIndexOutboxEventType.NOTE_UPSERT.equals(event.getEventType())) {
        searchClient.upsert(
            objectMapper.readValue(event.getPayload(), SearchClient.IndexDocumentRequest.class));
      } else if (SearchIndexOutboxEventType.NOTE_ARCHIVE.equals(event.getEventType())) {
        searchClient.archive(event.getNoteId());
      } else {
        throw new IllegalStateException("Unsupported search index outbox event type");
      }
      outboxService.markProcessed(event.getId());
    } catch (RuntimeException e) {
      log.warn(
          "Search index outbox dispatch failed eventId={} workspaceId={} noteId={} eventType={} errorClass={}",
          event.getId(),
          event.getWorkspaceId(),
          event.getNoteId(),
          event.getEventType(),
          e.getClass().getSimpleName());
      outboxService.markFailedOrRetry(event.getId(), e);
    } catch (Exception e) {
      RuntimeException wrapped =
          new IllegalStateException("Invalid search index outbox payload", e);
      outboxService.markFailedOrRetry(event.getId(), wrapped);
    } finally {
      sample.stop(meterRegistry.timer("search_outbox_processing_duration"));
    }
  }

  private boolean workerEnabled() {
    ContentProperties.Search search = properties.search();
    return search != null
        && search.enabled()
        && search.outbox() != null
        && search.outbox().workerEnabled();
  }

  private ContentProperties.SearchOutbox outbox() {
    ContentProperties.Search search = properties.search();
    if (search == null || search.outbox() == null) {
      return new ContentProperties.SearchOutbox(true, 50, 10, 30, 3600, 10, 300, null);
    }
    return search.outbox();
  }
}
