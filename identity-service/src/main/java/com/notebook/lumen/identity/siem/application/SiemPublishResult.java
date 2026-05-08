package com.notebook.lumen.identity.siem.application;

public record SiemPublishResult(boolean successful, boolean retryable, String message) {
  public static SiemPublishResult ok() {
    return new SiemPublishResult(true, false, "");
  }

  public static SiemPublishResult retryableFailure(String message) {
    return new SiemPublishResult(false, true, message);
  }

  public static SiemPublishResult nonRetryableFailure(String message) {
    return new SiemPublishResult(false, false, message);
  }
}
