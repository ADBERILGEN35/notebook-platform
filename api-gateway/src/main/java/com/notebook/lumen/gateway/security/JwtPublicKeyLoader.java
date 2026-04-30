package com.notebook.lumen.gateway.security;

import com.notebook.lumen.common.security.secrets.FileSecretProvider;
import com.notebook.lumen.common.security.secrets.SecretValue;
import com.notebook.lumen.gateway.config.GatewayJwtProperties;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class JwtPublicKeyLoader {

  private final GatewayJwtProperties properties;
  private final FileSecretProvider fileSecretProvider = new FileSecretProvider();

  public JwtPublicKeyLoader(GatewayJwtProperties properties) {
    this.properties = properties;
  }

  public RSAPublicKey load() {
    String path = properties.publicKeyPath();
    SecretValue pem = SecretValue.of("JWT_PUBLIC_KEY", properties.publicKey());

    if (path != null && !path.isBlank()) {
      try {
        return readPublicKey(fileSecretProvider.require(path).value());
      } catch (Exception e) {
        throw new IllegalStateException("Failed to load JWT public key from path", e);
      }
    }

    if (pem.hasText()) {
      try {
        return readPublicKey(pem.value());
      } catch (Exception e) {
        throw new IllegalStateException("Failed to load JWT public key from environment", e);
      }
    }

    throw new IllegalStateException("JWT_PUBLIC_KEY_PATH or JWT_PUBLIC_KEY must be configured");
  }

  private RSAPublicKey readPublicKey(String pem) throws Exception {
    byte[] der = parsePem(pem);
    X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
    return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
  }

  private byte[] parsePem(String pem) {
    Objects.requireNonNull(pem);
    String normalized = pem.replace("\r", "").trim();
    String begin = "-----BEGIN PUBLIC KEY-----";
    String end = "-----END PUBLIC KEY-----";

    if (!normalized.contains(begin) || !normalized.contains(end)) {
      throw new IllegalArgumentException("Unsupported PEM format. Missing public key markers.");
    }

    String base64 =
        normalized
            .substring(normalized.indexOf(begin) + begin.length(), normalized.indexOf(end))
            .replaceAll("\\s", "");

    return Base64.getDecoder().decode(base64);
  }
}
