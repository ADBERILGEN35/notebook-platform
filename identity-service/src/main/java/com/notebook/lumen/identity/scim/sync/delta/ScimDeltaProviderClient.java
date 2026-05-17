package com.notebook.lumen.identity.scim.sync.delta;

/** Read-only SCIM provider HTTP client (Faz 117). */
public interface ScimDeltaProviderClient {

  ScimDeltaProviderFetchResult fetch(ScimDeltaProviderRequest request, String bearerToken);
}
