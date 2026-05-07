package com.notebook.lumen.search.provider;

public enum SearchProviderType {
  POSTGRES,
  OPENSEARCH;

  public static SearchProviderType from(String value) {
    if (value == null || value.isBlank()) {
      return POSTGRES;
    }
    return switch (value.trim().toLowerCase()) {
      case "postgres", "postgresql" -> POSTGRES;
      case "opensearch", "elastic", "elasticsearch" -> OPENSEARCH;
      default -> throw new IllegalArgumentException("Unsupported search provider: " + value);
    };
  }
}
