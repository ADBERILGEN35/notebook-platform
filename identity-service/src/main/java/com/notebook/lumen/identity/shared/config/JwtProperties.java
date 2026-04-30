package com.notebook.lumen.identity.shared.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

  private long accessTokenTtlSeconds;
  private long refreshTokenTtlSeconds;
  private String privateKey;
  private String privateKeyPath;
  private String publicKey;
  private String publicKeyPath;
  private boolean allowEphemeralKeys = true;
  private Keys keys = new Keys();

  public long getAccessTokenTtlSeconds() {
    return accessTokenTtlSeconds;
  }

  public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
    this.accessTokenTtlSeconds = accessTokenTtlSeconds;
  }

  public long getRefreshTokenTtlSeconds() {
    return refreshTokenTtlSeconds;
  }

  public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
    this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
  }

  public String getPrivateKey() {
    return privateKey;
  }

  public void setPrivateKey(String privateKey) {
    this.privateKey = privateKey;
  }

  public String getPrivateKeyPath() {
    return privateKeyPath;
  }

  public void setPrivateKeyPath(String privateKeyPath) {
    this.privateKeyPath = privateKeyPath;
  }

  public String getPublicKey() {
    return publicKey;
  }

  public void setPublicKey(String publicKey) {
    this.publicKey = publicKey;
  }

  public String getPublicKeyPath() {
    return publicKeyPath;
  }

  public void setPublicKeyPath(String publicKeyPath) {
    this.publicKeyPath = publicKeyPath;
  }

  public boolean isAllowEphemeralKeys() {
    return allowEphemeralKeys;
  }

  public void setAllowEphemeralKeys(boolean allowEphemeralKeys) {
    this.allowEphemeralKeys = allowEphemeralKeys;
  }

  public Keys getKeys() {
    return keys;
  }

  public void setKeys(Keys keys) {
    this.keys = keys;
  }

  public static class Keys {
    private String activeKid;
    private List<SigningKey> signingKeys = new ArrayList<>();

    public String getActiveKid() {
      return activeKid;
    }

    public void setActiveKid(String activeKid) {
      this.activeKid = activeKid;
    }

    public List<SigningKey> getSigningKeys() {
      return signingKeys;
    }

    public void setSigningKeys(List<SigningKey> signingKeys) {
      this.signingKeys = signingKeys;
    }
  }

  public static class SigningKey {
    private String kid;
    private String privateKey;
    private String privateKeyPath;
    private String publicKey;
    private String publicKeyPath;

    public String getKid() {
      return kid;
    }

    public void setKid(String kid) {
      this.kid = kid;
    }

    public String getPrivateKey() {
      return privateKey;
    }

    public void setPrivateKey(String privateKey) {
      this.privateKey = privateKey;
    }

    public String getPrivateKeyPath() {
      return privateKeyPath;
    }

    public void setPrivateKeyPath(String privateKeyPath) {
      this.privateKeyPath = privateKeyPath;
    }

    public String getPublicKey() {
      return publicKey;
    }

    public void setPublicKey(String publicKey) {
      this.publicKey = publicKey;
    }

    public String getPublicKeyPath() {
      return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
      this.publicKeyPath = publicKeyPath;
    }
  }
}
