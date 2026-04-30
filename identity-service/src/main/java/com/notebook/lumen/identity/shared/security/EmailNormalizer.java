package com.notebook.lumen.identity.shared.security;

public final class EmailNormalizer {

  private EmailNormalizer() {}

  public static String normalize(String email) {
    if (email == null) {
      return null;
    }
    return email.trim().toLowerCase();
  }
}
