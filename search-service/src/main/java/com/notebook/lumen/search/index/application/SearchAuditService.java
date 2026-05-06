package com.notebook.lumen.search.index.application;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SearchAuditService {
  private static final Logger log = LoggerFactory.getLogger(SearchAuditService.class);

  public void record(
      String eventType, UUID workspaceId, UUID aggregateId, Map<String, ?> metadata) {
    log.info(
        "Search audit eventType={} workspaceId={} aggregateId={} metadata={}",
        eventType,
        workspaceId,
        aggregateId,
        metadata);
  }
}
