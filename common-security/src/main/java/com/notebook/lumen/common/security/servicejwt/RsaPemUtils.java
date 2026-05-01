package com.notebook.lumen.common.security.servicejwt;

import com.notebook.lumen.common.security.secrets.FileSecretProvider;
import com.notebook.lumen.common.security.secrets.SecretValue;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class RsaPemUtils {
  private static final FileSecretProvider FILE_SECRET_PROVIDER = new FileSecretProvider();

  private RsaPemUtils() {}

  static RSAPrivateKey loadPrivateKey(String name, String inlineKey, String keyPath) {
    String pem = resolvePem(name, inlineKey, keyPath);
    try {
      byte[] der = parsePem(pem, "BEGIN PRIVATE KEY", "END PRIVATE KEY");
      return (RSAPrivateKey)
          KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to load RSA private key for " + name);
    }
  }

  static RSAPublicKey loadPublicKey(String name, String inlineKey, String keyPath) {
    String pem = resolvePem(name, inlineKey, keyPath);
    try {
      byte[] der = parsePem(pem, "BEGIN PUBLIC KEY", "END PUBLIC KEY");
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to load RSA public key for " + name);
    }
  }

  private static String resolvePem(String name, String inlineKey, String keyPath) {
    SecretValue inline = SecretValue.of(name, inlineKey);
    if (inline.hasText()) {
      return inline.value();
    }
    if (keyPath != null && !keyPath.isBlank()) {
      return FILE_SECRET_PROVIDER.require(keyPath).value();
    }
    throw new IllegalArgumentException("Missing RSA key material for " + name);
  }

  private static byte[] parsePem(String pem, String beginMarker, String endMarker) {
    String normalized = pem.replace("\r", "").trim();
    int begin = normalized.indexOf(beginMarker);
    int end = normalized.indexOf(endMarker);
    if (begin < 0 || end < 0 || end <= begin) {
      throw new IllegalArgumentException("Invalid PEM");
    }
    String base64 =
        normalized
            .substring(begin + beginMarker.length(), end)
            .replace("-", "")
            .replaceAll("\\s", "");
    return Base64.getDecoder().decode(base64);
  }
}
