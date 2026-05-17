package com.notebook.lumen.identity.scim.sync.delta;

/** Warning codes for delta remote fetch (Faz 117). */
public final class ScimDeltaRemoteFetchWarnings {

  public static final String REMOTE_FETCH_NOT_CONFIGURED = "SCIM_DELTA_REMOTE_FETCH_NOT_CONFIGURED";
  public static final String REMOTE_FETCH_ATTEMPTED = "SCIM_DELTA_REMOTE_FETCH_ATTEMPTED";
  public static final String REMOTE_FETCH_SUPPRESSED = "SCIM_DELTA_REMOTE_FETCH_SUPPRESSED";
  public static final String PROVIDER_RESPONSE_SANITIZED = "SCIM_DELTA_PROVIDER_RESPONSE_SANITIZED";
  public static final String NEXT_CURSOR_PRESENT = "SCIM_DELTA_NEXT_CURSOR_PRESENT";
  public static final String REMOTE_PAGE_SIZE_CAPPED = "SCIM_DELTA_REMOTE_PAGE_SIZE_CAPPED";

  private ScimDeltaRemoteFetchWarnings() {}
}
