package com.notebook.lumen.identity.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.notebook.lumen.identity.shared.config.JwtProperties;
import com.notebook.lumen.identity.shared.exception.TokenGenerationException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JwtKeyProviderTest {
  @TempDir Path tempDir;

  @Test
  void loadOrGenerateKeys_rejectsEphemeralKeysWhenDisabled() {
    JwtProperties props = new JwtProperties();
    props.setAllowEphemeralKeys(false);
    props.setPrivateKeyPath("");
    props.setPublicKeyPath("");

    JwtKeyProvider provider = new JwtKeyProvider(props);

    assertThatThrownBy(provider::loadOrGenerateKeys)
        .isInstanceOf(TokenGenerationException.class)
        .hasMessageContaining("JWT key paths are required");
  }

  @Test
  void loadOrGenerateKeySet_rejectsDuplicateKids() throws Exception {
    KeyFiles first = writeKeyFiles("first");
    KeyFiles second = writeKeyFiles("second");
    JwtProperties props = new JwtProperties();
    props.getKeys().setActiveKid("key-1");
    props
        .getKeys()
        .setSigningKeys(
            java.util.List.of(
                signingKey("key-1", first.privateKeyPath(), first.publicKeyPath()),
                signingKey("key-1", second.privateKeyPath(), second.publicKeyPath())));

    assertThatThrownBy(() -> new JwtKeyProvider(props).loadOrGenerateKeySet())
        .isInstanceOf(TokenGenerationException.class)
        .hasMessageContaining("Duplicate JWT signing key kid");
  }

  @Test
  void loadOrGenerateKeySet_rejectsUnknownActiveKid() throws Exception {
    KeyFiles keyFiles = writeKeyFiles("single");
    JwtProperties props = new JwtProperties();
    props.getKeys().setActiveKid("missing");
    props
        .getKeys()
        .setSigningKeys(
            java.util.List.of(
                signingKey("key-1", keyFiles.privateKeyPath(), keyFiles.publicKeyPath())));

    assertThatThrownBy(() -> new JwtKeyProvider(props).loadOrGenerateKeySet())
        .isInstanceOf(TokenGenerationException.class)
        .hasMessageContaining("JWT active kid is not present");
  }

  @Test
  void loadOrGenerateKeySet_loadsMultipleConfiguredKeys() throws Exception {
    KeyFiles first = writeKeyFiles("first");
    KeyFiles second = writeKeyFiles("second");
    JwtProperties props = new JwtProperties();
    props.getKeys().setActiveKid("key-2");
    props
        .getKeys()
        .setSigningKeys(
            java.util.List.of(
                signingKey("key-1", first.privateKeyPath(), first.publicKeyPath()),
                signingKey("key-2", second.privateKeyPath(), second.publicKeyPath())));

    JwtKeyProvider.JwtRsaKeySet keySet = new JwtKeyProvider(props).loadOrGenerateKeySet();

    assertThat(keySet.activeKid()).isEqualTo("key-2");
    assertThat(keySet.keys())
        .extracting(JwtKeyProvider.JwtRsaKeys::kid)
        .containsExactly("key-1", "key-2");
  }

  private JwtProperties.SigningKey signingKey(
      String kid, String privateKeyPath, String publicKeyPath) {
    JwtProperties.SigningKey signingKey = new JwtProperties.SigningKey();
    signingKey.setKid(kid);
    signingKey.setPrivateKeyPath(privateKeyPath);
    signingKey.setPublicKeyPath(publicKeyPath);
    return signingKey;
  }

  private KeyFiles writeKeyFiles(String prefix) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keyPair = generator.generateKeyPair();
    Path privateKeyPath = tempDir.resolve(prefix + "-private.pem");
    Path publicKeyPath = tempDir.resolve(prefix + "-public.pem");
    Files.writeString(
        privateKeyPath,
        pem("PRIVATE KEY", keyPair.getPrivate().getEncoded()),
        StandardCharsets.UTF_8);
    Files.writeString(
        publicKeyPath, pem("PUBLIC KEY", keyPair.getPublic().getEncoded()), StandardCharsets.UTF_8);
    return new KeyFiles(privateKeyPath.toString(), publicKeyPath.toString());
  }

  private String pem(String marker, byte[] der) {
    return "-----BEGIN "
        + marker
        + "-----\n"
        + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(der)
        + "\n-----END "
        + marker
        + "-----\n";
  }

  private record KeyFiles(String privateKeyPath, String publicKeyPath) {}
}
