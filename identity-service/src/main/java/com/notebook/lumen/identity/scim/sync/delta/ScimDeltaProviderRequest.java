package com.notebook.lumen.identity.scim.sync.delta;

import com.notebook.lumen.identity.scim.sync.ScimResourceType;

/** Read-only provider fetch request (Faz 117). URI must not contain credentials. */
public record ScimDeltaProviderRequest(
    ScimDeltaHttpMethod method, String requestUri, ScimResourceType resourceType, int pageSize) {

  public ScimDeltaProviderRequest {
    if (method != ScimDeltaHttpMethod.GET) {
      throw new IllegalArgumentException("Only GET is allowed for delta remote fetch");
    }
    if (requestUri == null || requestUri.isBlank()) {
      throw new IllegalArgumentException("requestUri is required");
    }
    if (requestUri.toLowerCase().contains("bearer") || requestUri.contains("@")) {
      throw new IllegalArgumentException("requestUri must not contain credentials");
    }
  }
}
