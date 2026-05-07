package com.notebook.lumen.workspace.client;

import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange
public interface SearchPermissionRefreshClient {

  @PostExchange("/internal/search/permissions/notebooks/{notebookId}/refresh")
  void refreshNotebookPermissionSnapshot(@PathVariable UUID notebookId);
}

