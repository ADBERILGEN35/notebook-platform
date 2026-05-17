package com.notebook.lumen.identity.scim.sync.delta;

/** Why a read-only remote page loop stopped (Faz 119). */
public enum ScimDeltaStoppedReason {
  SINGLE_PAGE_ONLY,
  NO_NEXT_CURSOR,
  PAGE_LIMIT_REACHED,
  RESOURCE_LIMIT_REACHED,
  PROVIDER_RATE_LIMITED,
  PROVIDER_ERROR,
  TIMEOUT,
  BAD_RESPONSE,
  REMOTE_FETCH_DISABLED,
  NOT_CONFIGURED,
  COMPLETED
}
