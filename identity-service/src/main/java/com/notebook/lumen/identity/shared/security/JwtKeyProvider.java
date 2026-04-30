package com.notebook.lumen.identity.shared.security;

import com.notebook.lumen.common.security.secrets.FileSecretProvider;
import com.notebook.lumen.common.security.secrets.SecretValue;
import com.notebook.lumen.identity.shared.config.JwtProperties;
import com.notebook.lumen.identity.shared.exception.TokenGenerationException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtKeyProvider {

  private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

  private final JwtProperties props;
  private final FileSecretProvider fileSecretProvider = new FileSecretProvider();
  private JwtRsaKeySet cachedKeySet;

  public JwtKeyProvider(JwtProperties props) {
    this.props = props;
  }

  public JwtRsaKeys loadOrGenerateKeys() {
    return loadOrGenerateKeySet().activeKey();
  }

  public JwtRsaKeySet loadOrGenerateKeySet() {
    if (cachedKeySet != null) {
      return cachedKeySet;
    }

    List<JwtProperties.SigningKey> configuredKeys = configuredSigningKeys();
    if (configuredKeys.isEmpty()) {
      cachedKeySet = loadLegacyOrEphemeralKeySet();
      return cachedKeySet;
    }

    String activeKid = activeKid();
    List<JwtRsaKeys> keys = new ArrayList<>();
    Set<String> kids = new HashSet<>();
    for (JwtProperties.SigningKey configuredKey : configuredKeys) {
      String kid = configuredKey.getKid();
      if (!hasText(kid)) {
        throw new TokenGenerationException("JWT signing key kid is required.");
      }
      if (!kids.add(kid)) {
        throw new TokenGenerationException("Duplicate JWT signing key kid configured: " + kid);
      }
      keys.add(loadConfiguredKey(configuredKey));
    }
    JwtRsaKeys activeKey =
        keys.stream()
            .filter(key -> key.kid().equals(activeKid))
            .findFirst()
            .orElseThrow(
                () ->
                    new TokenGenerationException(
                        "JWT active kid is not present in configured signing keys: " + activeKid));
    cachedKeySet = new JwtRsaKeySet(activeKid, activeKey, List.copyOf(keys));
    return cachedKeySet;
  }

  public Optional<RSAPublicKey> publicKey(String kid) {
    if (!hasText(kid)) {
      return Optional.empty();
    }
    return loadOrGenerateKeySet().keys().stream()
        .filter(key -> key.kid().equals(kid))
        .map(JwtRsaKeys::publicKey)
        .findFirst();
  }

  private JwtRsaKeySet loadLegacyOrEphemeralKeySet() {
    SecretValue privateSecret = SecretValue.of("JWT_PRIVATE_KEY", props.getPrivateKey());
    SecretValue publicSecret = SecretValue.of("JWT_PUBLIC_KEY", props.getPublicKey());

    boolean hasPrivate = privateSecret.hasText() || hasText(props.getPrivateKeyPath());
    boolean hasPublic = publicSecret.hasText() || hasText(props.getPublicKeyPath());

    if (!hasPrivate && !hasPublic) {
      if (!props.isAllowEphemeralKeys()) {
        throw new TokenGenerationException(
            "JWT key paths are required when ephemeral key generation is disabled.");
      }
      log.warn(
          "JWT key paths not configured. Generating ephemeral RSA keys for this runtime (dev/test only).");
      JwtRsaKeys keys = generateEphemeralKeys(activeKid());
      return new JwtRsaKeySet(keys.kid(), keys, List.of(keys));
    }

    JwtRsaKeys keys =
        loadKey(
            activeKid(),
            privateSecret,
            props.getPrivateKeyPath(),
            publicSecret,
            props.getPublicKeyPath());
    return new JwtRsaKeySet(keys.kid(), keys, List.of(keys));
  }

  private JwtRsaKeys loadConfiguredKey(JwtProperties.SigningKey configuredKey) {
    return loadKey(
        configuredKey.getKid(),
        SecretValue.of("JWT_PRIVATE_KEY", configuredKey.getPrivateKey()),
        configuredKey.getPrivateKeyPath(),
        SecretValue.of("JWT_PUBLIC_KEY", configuredKey.getPublicKey()),
        configuredKey.getPublicKeyPath());
  }

  private JwtRsaKeys loadKey(
      String kid,
      SecretValue privateSecret,
      String privateKeyPath,
      SecretValue publicSecret,
      String publicKeyPath) {
    RSAPrivateKey privateKey = null;
    RSAPublicKey publicKey = null;
    try {
      if (privateSecret.hasText() || hasText(privateKeyPath)) {
        privateKey = readPrivateKey(privateSecret, privateKeyPath);
      }
      if (publicSecret.hasText() || hasText(publicKeyPath)) {
        publicKey = readPublicKey(publicSecret, publicKeyPath);
      }
    } catch (Exception e) {
      throw new TokenGenerationException("Failed to load RSA keys: " + e.getMessage());
    }

    if (publicKey == null && privateKey != null) {
      try {
        publicKey = derivePublicKey(privateKey);
      } catch (Exception e) {
        throw new TokenGenerationException("Failed to derive public key: " + e.getMessage());
      }
    }

    if (privateKey == null) {
      throw new TokenGenerationException(
          "JWT private key is required for signing tokens but was not configured.");
    }
    if (publicKey == null) {
      throw new TokenGenerationException(
          "JWT public key is required for verifying tokens but was not configured.");
    }

    return new JwtRsaKeys(kid, privateKey, publicKey);
  }

  private RSAPrivateKey readPrivateKey(SecretValue inlineKey, String privateKeyPath)
      throws Exception {
    String pem =
        inlineKey.hasText()
            ? inlineKey.value()
            : fileSecretProvider.require(privateKeyPath).value();
    byte[] der = parsePem(pem, "BEGIN PRIVATE KEY", "END PRIVATE KEY");
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
    KeyFactory factory = KeyFactory.getInstance("RSA");
    return (RSAPrivateKey) factory.generatePrivate(spec);
  }

  private RSAPublicKey readPublicKey(SecretValue inlineKey, String publicKeyPath) throws Exception {
    String pem =
        inlineKey.hasText() ? inlineKey.value() : fileSecretProvider.require(publicKeyPath).value();
    byte[] der = parsePem(pem, "BEGIN PUBLIC KEY", "END PUBLIC KEY");
    X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
    KeyFactory factory = KeyFactory.getInstance("RSA");
    return (RSAPublicKey) factory.generatePublic(spec);
  }

  private RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) throws Exception {
    if (!(privateKey instanceof RSAPrivateCrtKey crtKey)) {
      throw new TokenGenerationException(
          "Cannot derive public key from provided private key type.");
    }
    RSAPublicKeySpec spec = new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
    return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
  }

  private JwtRsaKeys generateEphemeralKeys(String kid) {
    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
      keyPairGenerator.initialize(2048);
      KeyPair keyPair = keyPairGenerator.generateKeyPair();
      return new JwtRsaKeys(
          kid, (RSAPrivateKey) keyPair.getPrivate(), (RSAPublicKey) keyPair.getPublic());
    } catch (Exception e) {
      throw new TokenGenerationException(
          "Failed to generate ephemeral RSA keys: " + e.getMessage());
    }
  }

  private byte[] parsePem(String pem, String beginMarker, String endMarker) {
    Objects.requireNonNull(pem);
    String normalized = pem.replace("\r", "").trim();
    String begin = "-----" + beginMarker + "-----";
    String end = "-----" + endMarker + "-----";

    if (!normalized.contains(begin) || !normalized.contains(end)) {
      throw new IllegalArgumentException("Unsupported PEM format. Missing " + beginMarker);
    }

    String base64 =
        normalized
            .substring(normalized.indexOf(begin) + begin.length(), normalized.indexOf(end))
            .replaceAll("\\s", "");

    return Base64.getDecoder().decode(base64);
  }

  private List<JwtProperties.SigningKey> configuredSigningKeys() {
    if (props.getKeys() == null || props.getKeys().getSigningKeys() == null) {
      return List.of();
    }
    return props.getKeys().getSigningKeys().stream().filter(this::hasAnyKeyMaterial).toList();
  }

  private boolean hasAnyKeyMaterial(JwtProperties.SigningKey key) {
    return key != null
        && (hasText(key.getPrivateKey())
            || hasText(key.getPrivateKeyPath())
            || hasText(key.getPublicKey())
            || hasText(key.getPublicKeyPath()));
  }

  private String activeKid() {
    String configured = props.getKeys() == null ? null : props.getKeys().getActiveKid();
    return hasText(configured) ? configured : "primary";
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  public record JwtRsaKeys(String kid, RSAPrivateKey privateKey, RSAPublicKey publicKey) {}

  public record JwtRsaKeySet(String activeKid, JwtRsaKeys activeKey, List<JwtRsaKeys> keys) {}
}
