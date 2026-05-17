package com.notebook.lumen.identity.scim.sync;

/** Sanitized provider fetch error classification (Faz 116). */
public enum ScimProviderErrorClass {
  NONE,
  RATE_LIMITED,
  RETRY_AFTER_OBSERVED,
  TIMEOUT,
  PROVIDER_UNAVAILABLE,
  PROVIDER_AUTH_FAILED,
  PROVIDER_BAD_RESPONSE,
  PROVIDER_UNSUPPORTED,
  REMOTE_FETCH_DISABLED
}
