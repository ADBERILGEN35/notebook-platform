package com.notebook.lumen.notification.admin.deadletter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

final class DeadLetterRecipientHasher {

  private DeadLetterRecipientHasher() {}

  static String hash(UUID recipientUserId, String pepper) {
    String material =
        "v1|" + (pepper == null || pepper.isBlank() ? "no-pepper" : pepper) + "|" + recipientUserId;
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(material.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
