package com.notebook.lumen.search.reindex.application;

import com.notebook.lumen.search.index.api.IndexDocumentRequest;
import com.notebook.lumen.search.index.application.SearchIndexService;
import com.notebook.lumen.search.reindex.client.ContentSearchIndexSourceClient;
import com.notebook.lumen.search.reindex.client.ContentSearchIndexSourceClient.SearchIndexSourceNoteResponse;
import com.notebook.lumen.search.reindex.client.ContentSearchIndexSourceClient.SearchIndexSourcePageResponse;
import com.notebook.lumen.search.reindex.domain.SearchReindexJob;
import com.notebook.lumen.search.shared.config.SearchProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SearchReindexWorker {
  private static final Logger log = LoggerFactory.getLogger(SearchReindexWorker.class);

  private final SearchReindexService reindexService;
  private final ContentSearchIndexSourceClient sourceClient;
  private final SearchIndexService indexService;
  private final SearchProperties properties;
  private final MeterRegistry meterRegistry;
  private final AtomicBoolean acceptingClaims = new AtomicBoolean(true);

  public SearchReindexWorker(
      SearchReindexService reindexService,
      ContentSearchIndexSourceClient sourceClient,
      SearchIndexService indexService,
      SearchProperties properties,
      MeterRegistry meterRegistry) {
    this.reindexService = reindexService;
    this.sourceClient = sourceClient;
    this.indexService = indexService;
    this.properties = properties;
    this.meterRegistry = meterRegistry;
  }

  @Scheduled(fixedDelayString = "${search.reindex.poll-interval-seconds:10}000")
  public void poll() {
    if (!acceptingClaims.get() || !properties.reindex().workerEnabled()) {
      return;
    }
    reindexService.claimNextPending().ifPresent(this::runJob);
  }

  @jakarta.annotation.PreDestroy
  void stopAcceptingClaims() {
    acceptingClaims.set(false);
  }

  private void runJob(SearchReindexJob job) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      String cursor = job.getLastCursor();
      boolean hasNext;
      Instant nextHeartbeatAt = Instant.EPOCH;
      do {
        if (reindexService.cancelled(job.getId())) {
          return;
        }
        Instant now = Instant.now();
        if (!now.isBefore(nextHeartbeatAt)) {
          reindexService.heartbeat(job.getId());
          nextHeartbeatAt =
              now.plusSeconds(properties.reindex().effectiveHeartbeatIntervalSeconds());
        }
        SearchIndexSourcePageResponse page =
            sourceClient.notes(
                job.getWorkspaceId(),
                job.getNotebookId(),
                cursor,
                properties.reindex().effectiveBatchSize());
        long indexed = 0;
        long failed = 0;
        for (SearchIndexSourceNoteResponse item : page.items()) {
          try {
            indexService.upsertForReindex(toIndexRequest(item), job.getId());
            indexed++;
          } catch (RuntimeException e) {
            failed++;
            log.warn(
                "Reindex item failed jobId={} workspaceId={} noteId={} errorClass={}",
                job.getId(),
                item.workspaceId(),
                item.noteId(),
                e.getClass().getSimpleName());
          }
        }
        job =
            reindexService.recordBatch(
                job.getId(), page.items().size(), indexed, failed, page.nextCursor());
        if (job.getTotalFailed() > properties.reindex().effectiveMaxFailures()) {
          throw new IllegalStateException("Search reindex max failures exceeded");
        }
        cursor = page.nextCursor();
        hasNext = page.hasNext();
      } while (hasNext);
      if (!reindexService.cancelled(job.getId())) {
        reindexService.cleanupAfterSuccessfulScan(job.getId());
        reindexService.complete(job.getId());
      }
    } catch (RuntimeException e) {
      reindexService.fail(job.getId(), e);
    } finally {
      sample.stop(meterRegistry.timer("search_reindex_duration"));
    }
  }

  private IndexDocumentRequest toIndexRequest(SearchIndexSourceNoteResponse item) {
    return new IndexDocumentRequest(
        item.workspaceId(),
        item.notebookId(),
        item.noteId(),
        item.title(),
        item.contentBlocks(),
        item.tags(),
        item.notebookName(),
        item.createdBy(),
        item.updatedBy(),
        item.noteCreatedAt(),
        item.noteUpdatedAt(),
        item.archivedAt(),
        item.sourceVersion());
  }
}
