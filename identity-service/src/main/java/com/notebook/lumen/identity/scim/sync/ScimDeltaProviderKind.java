package com.notebook.lumen.identity.scim.sync;

import java.util.Locale;

/** Normalized SCIM delta POC provider kinds (Faz 115). */
public enum ScimDeltaProviderKind {
  OKTA,
  AZURE_AD,
  GENERIC;

  public static ScimDeltaProviderKind fromConfig(String providerType) {
    if (providerType == null || providerType.isBlank()) {
      return GENERIC;
    }
    String normalized = providerType.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "okta" -> OKTA;
      case "azure-ad", "azure_ad", "entra", "entra-id", "entra_id", "microsoft-entra", "microsoft_entra" ->
          AZURE_AD;
      default -> GENERIC;
    };
  }

  public String configValue() {
    return switch (this) {
      case OKTA -> "okta";
      case AZURE_AD -> "azure-ad";
      case GENERIC -> "generic";
    };
  }
}
