package com.notebook.lumen.identity.scim.sync;

import java.util.ArrayList;
import java.util.List;

/** Maps HTTP outcomes to sanitized provider error classes (Faz 116). */
public final class ScimProviderResponseClassifier {

  static final String WARNING_RATE_LIMITED = "SCIM_DELTA_RATE_LIMITED";
  static final String WARNING_PROVIDER_TIMEOUT = "SCIM_DELTA_PROVIDER_TIMEOUT";
  static final String WARNING_PROVIDER_UNAVAILABLE = "SCIM_DELTA_PROVIDER_UNAVAILABLE";
  static final String WARNING_PROVIDER_AUTH_FAILED = "SCIM_DELTA_PROVIDER_AUTH_FAILED";
  static final String WARNING_PROVIDER_BAD_RESPONSE = "SCIM_DELTA_PROVIDER_BAD_RESPONSE";
  static final String WARNING_BACKOFF_RECOMMENDED = "SCIM_DELTA_BACKOFF_RECOMMENDED";
  static final String WARNING_REMOTE_FETCH_DISABLED = "SCIM_DELTA_REMOTE_FETCH_DISABLED";

  private ScimProviderResponseClassifier() {}

  public record Classification(
      ScimProviderErrorClass errorClass, boolean retryable, List<String> warnings) {}

  public static Classification classifyHttpStatus(int httpStatus) {
    List<String> warnings = new ArrayList<>();
    if (httpStatus == 429) {
      warnings.add(WARNING_RATE_LIMITED);
      warnings.add(WARNING_BACKOFF_RECOMMENDED);
      return new Classification(ScimProviderErrorClass.RATE_LIMITED, true, warnings);
    }
    if (httpStatus == 401 || httpStatus == 403) {
      warnings.add(WARNING_PROVIDER_AUTH_FAILED);
      return new Classification(ScimProviderErrorClass.PROVIDER_AUTH_FAILED, false, warnings);
    }
    if (httpStatus == 502 || httpStatus == 503 || httpStatus == 504) {
      warnings.add(WARNING_PROVIDER_UNAVAILABLE);
      warnings.add(WARNING_BACKOFF_RECOMMENDED);
      return new Classification(ScimProviderErrorClass.PROVIDER_UNAVAILABLE, true, warnings);
    }
    if (httpStatus >= 500) {
      warnings.add(WARNING_PROVIDER_UNAVAILABLE);
      warnings.add(WARNING_BACKOFF_RECOMMENDED);
      return new Classification(ScimProviderErrorClass.PROVIDER_UNAVAILABLE, true, warnings);
    }
    if (httpStatus >= 400) {
      warnings.add(WARNING_PROVIDER_BAD_RESPONSE);
      return new Classification(ScimProviderErrorClass.PROVIDER_BAD_RESPONSE, false, warnings);
    }
    return new Classification(ScimProviderErrorClass.NONE, false, warnings);
  }

  public static Classification classifyTimeout() {
    return new Classification(
        ScimProviderErrorClass.TIMEOUT,
        true,
        List.of(WARNING_PROVIDER_TIMEOUT, WARNING_BACKOFF_RECOMMENDED));
  }

  public static Classification classifyBadResponse() {
    return new Classification(
        ScimProviderErrorClass.PROVIDER_BAD_RESPONSE,
        false,
        List.of(WARNING_PROVIDER_BAD_RESPONSE));
  }

  public static Classification classifyRemoteFetchDisabled() {
    return new Classification(
        ScimProviderErrorClass.REMOTE_FETCH_DISABLED,
        false,
        List.of(WARNING_REMOTE_FETCH_DISABLED));
  }
}
