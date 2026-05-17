package com.notebook.lumen.identity.scim.sync;

/** Provider delta sync strategy selection (diagnostic / POC only — Faz 115). */
public enum ScimDeltaSyncStrategy {
  DISABLED,
  LAST_MODIFIED_FILTER,
  CURSOR_CHECKPOINT,
  FULL_SYNC_FALLBACK
}
