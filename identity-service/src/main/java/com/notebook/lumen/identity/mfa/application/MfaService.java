package com.notebook.lumen.identity.mfa.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notebook.lumen.identity.auth.api.AuthResponse;
import com.notebook.lumen.identity.mfa.MfaProperties;
import com.notebook.lumen.identity.mfa.domain.UserMfaRecoveryCode;
import com.notebook.lumen.identity.mfa.domain.UserMfaSettings;
import com.notebook.lumen.identity.mfa.domain.UserWebAuthnCredential;
import com.notebook.lumen.identity.mfa.infrastructure.UserMfaRecoveryCodeRepository;
import com.notebook.lumen.identity.mfa.infrastructure.UserMfaSettingsRepository;
import com.notebook.lumen.identity.mfa.infrastructure.UserWebAuthnCredentialRepository;
import com.notebook.lumen.identity.shared.exception.MfaException;
import com.notebook.lumen.identity.user.domain.User;
import com.notebook.lumen.identity.user.mapper.UserMapper;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MfaService {
  private final MfaProperties properties;
  private final StringRedisTemplate redisTemplate;
  private final UserMfaSettingsRepository settingsRepository;
  private final UserWebAuthnCredentialRepository credentialRepository;
  private final UserMfaRecoveryCodeRepository recoveryCodeRepository;
  private final PasswordEncoder passwordEncoder;
  private final UserMapper userMapper;
  private final ObjectMapper objectMapper;
  private final SecureRandom secureRandom = new SecureRandom();

  public MfaService(
      MfaProperties properties,
      StringRedisTemplate redisTemplate,
      UserMfaSettingsRepository settingsRepository,
      UserWebAuthnCredentialRepository credentialRepository,
      UserMfaRecoveryCodeRepository recoveryCodeRepository,
      PasswordEncoder passwordEncoder,
      UserMapper userMapper,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.redisTemplate = redisTemplate;
    this.settingsRepository = settingsRepository;
    this.credentialRepository = credentialRepository;
    this.recoveryCodeRepository = recoveryCodeRepository;
    this.passwordEncoder = passwordEncoder;
    this.userMapper = userMapper;
    this.objectMapper = objectMapper;
  }

  public boolean isMfaRequired(User user) {
    if (!properties.enabled()) {
      return false;
    }
    UserMfaSettings settings =
        settingsRepository
            .findById(user.getId())
            .orElse(
                new UserMfaSettings(
                    user.getId(), false, false, false, Instant.now(), Instant.now()));
    return settings.isWebauthnEnabled() || settings.isMfaRequired();
  }

  public AuthResponse startMfaChallenge(User user) {
    String sessionId = UUID.randomUUID().toString();
    saveRedis(
        mfaSessionKey(sessionId),
        new MfaSessionPayload(
            user.getId().toString(), Instant.now().plusSeconds(ttlSeconds()).toString()));
    return new AuthResponse(
        null,
        null,
        "MFA",
        0,
        userMapper.toResponse(user),
        true,
        sessionId,
        List.of("WEBAUTHN", "RECOVERY_CODE"));
  }

  public int recoveryCodesRemaining(UUID userId) {
    return (int)
        recoveryCodeRepository.findByUserId(userId).stream()
            .filter(code -> code.getUsedAt() == null && code.getRevokedAt() == null)
            .count();
  }

  public UUID requireMfaSessionUser(String mfaSessionId) {
    MfaSessionPayload payload = readRedis(mfaSessionKey(mfaSessionId), MfaSessionPayload.class);
    if (payload == null) {
      throw new MfaException(
          "MFA_SESSION_INVALID", HttpStatus.UNAUTHORIZED, "MFA session is invalid");
    }
    return UUID.fromString(payload.userId());
  }

  public void consumeMfaSession(String mfaSessionId) {
    redisTemplate.delete(mfaSessionKey(mfaSessionId));
  }

  public String createChallenge(String keyPrefix, String refId) {
    String challenge = randomToken();
    saveRedis(
        keyPrefix + refId,
        new ChallengePayload(challenge, Instant.now().plusSeconds(ttlSeconds()).toString()));
    return challenge;
  }

  public void verifyAndConsumeChallenge(String keyPrefix, String refId, String challenge) {
    String key = keyPrefix + refId;
    ChallengePayload payload = readRedis(key, ChallengePayload.class);
    if (payload == null) {
      throw new MfaException(
          "MFA_CHALLENGE_EXPIRED", HttpStatus.UNAUTHORIZED, "MFA challenge expired");
    }
    redisTemplate.delete(key);
    if (!payload.challenge().equals(challenge)) {
      throw new MfaException(
          "MFA_CHALLENGE_INVALID", HttpStatus.UNAUTHORIZED, "MFA challenge invalid");
    }
  }

  @Transactional
  public List<String> regenerateRecoveryCodes(UUID userId) {
    requireEnabled();
    List<UserMfaRecoveryCode> existing = recoveryCodeRepository.findByUserId(userId);
    existing.stream().filter(code -> code.getUsedAt() == null).forEach(UserMfaRecoveryCode::revoke);
    recoveryCodeRepository.saveAll(existing);
    List<String> plainCodes =
        java.util.stream.IntStream.range(0, 10).mapToObj(i -> formatRecoveryCode()).toList();
    recoveryCodeRepository.saveAll(
        plainCodes.stream()
            .map(
                code ->
                    new UserMfaRecoveryCode(
                        UUID.randomUUID(),
                        userId,
                        passwordEncoder.encode(code),
                        null,
                        null,
                        Instant.now()))
            .toList());
    UserMfaSettings settings =
        settingsRepository
            .findById(userId)
            .orElse(new UserMfaSettings(userId, false, true, false, Instant.now(), Instant.now()));
    settings.setBackupCodesEnabled(true);
    settingsRepository.save(settings);
    return plainCodes;
  }

  @Transactional
  public void verifyRecoveryCode(UUID userId, String code) {
    List<UserMfaRecoveryCode> codes = recoveryCodeRepository.findByUserId(userId);
    for (UserMfaRecoveryCode candidate : codes) {
      if (candidate.getUsedAt() != null) {
        continue;
      }
      if (candidate.getRevokedAt() != null) {
        continue;
      }
      if (passwordEncoder.matches(code, candidate.getCodeHash())) {
        candidate.markUsed();
        recoveryCodeRepository.save(candidate);
        return;
      }
    }
    if (codes.stream()
        .anyMatch(c -> c.getUsedAt() != null && passwordEncoder.matches(code, c.getCodeHash()))) {
      throw new MfaException(
          "MFA_RECOVERY_CODE_USED", HttpStatus.UNAUTHORIZED, "Recovery code already used");
    }
    throw new MfaException(
        "MFA_RECOVERY_CODE_INVALID", HttpStatus.UNAUTHORIZED, "Invalid recovery code");
  }

  @Transactional
  public UserWebAuthnCredential storeCredential(
      UUID userId, String credentialId, String publicKey, Long signCount, String name) {
    if (credentialRepository.findByCredentialId(credentialId).isPresent()) {
      throw new MfaException(
          "WEBAUTHN_CREDENTIAL_ALREADY_REGISTERED",
          HttpStatus.CONFLICT,
          "WebAuthn credential already registered");
    }
    UserWebAuthnCredential credential =
        credentialRepository.save(
            new UserWebAuthnCredential(
                UUID.randomUUID(),
                userId,
                credentialId,
                publicKey,
                signCount,
                "none",
                null,
                name,
                null,
                Instant.now(),
                null));
    UserMfaSettings settings =
        settingsRepository
            .findById(userId)
            .orElse(new UserMfaSettings(userId, true, false, false, Instant.now(), Instant.now()));
    settings.setWebauthnEnabled(true);
    settingsRepository.save(settings);
    return credential;
  }

  public List<UserWebAuthnCredential> activeCredentials(UUID userId) {
    return credentialRepository.findByUserIdAndRevokedAtIsNull(userId);
  }

  public boolean hasRecoveryCodes(UUID userId) {
    return recoveryCodeRepository.findByUserId(userId).stream()
        .anyMatch(code -> code.getUsedAt() == null && code.getRevokedAt() == null);
  }

  @Transactional
  public void renameCredential(UUID userId, String credentialId, String name) {
    UserWebAuthnCredential credential =
        credentialRepository
            .findByCredentialId(credentialId)
            .orElseThrow(
                () ->
                    new MfaException(
                        "WEBAUTHN_CREDENTIAL_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "Credential not found"));
    if (!credential.getUserId().equals(userId) || credential.getRevokedAt() != null) {
      throw new MfaException(
          "WEBAUTHN_CREDENTIAL_NOT_FOUND", HttpStatus.NOT_FOUND, "Credential not found");
    }
    credential.setName(name);
    credentialRepository.save(credential);
  }

  @Transactional
  public void removeCredential(UUID userId, String credentialId) {
    UserWebAuthnCredential credential =
        credentialRepository
            .findByCredentialId(credentialId)
            .orElseThrow(
                () ->
                    new MfaException(
                        "WEBAUTHN_CREDENTIAL_NOT_FOUND",
                        HttpStatus.NOT_FOUND,
                        "Credential not found"));
    if (!credential.getUserId().equals(userId) || credential.getRevokedAt() != null) {
      throw new MfaException(
          "WEBAUTHN_CREDENTIAL_NOT_FOUND", HttpStatus.NOT_FOUND, "Credential not found");
    }
    if (activeCredentials(userId).size() <= 1) {
      throw new MfaException(
          "MFA_LAST_CREDENTIAL_CANNOT_BE_REMOVED",
          HttpStatus.BAD_REQUEST,
          "Last credential cannot be removed");
    }
    credential.revoke();
    credentialRepository.save(credential);
  }

  public void requireEnabled() {
    if (!properties.enabled()) {
      throw new MfaException("MFA_NOT_ENABLED", HttpStatus.NOT_IMPLEMENTED, "MFA is not enabled");
    }
  }

  public void requireWebauthnEnabled() {
    requireEnabled();
    if (!properties.webauthnEnabled()) {
      throw new MfaException(
          "MFA_NOT_ENABLED", HttpStatus.NOT_IMPLEMENTED, "WebAuthn is not enabled");
    }
  }

  public boolean isAllowedOrigin(String origin) {
    String allowed = properties.webauthn() == null ? "" : properties.webauthn().allowedOrigins();
    return List.of(allowed.split(",")).stream().map(String::trim).anyMatch(origin::equals);
  }

  private int ttlSeconds() {
    return Math.max(30, properties.challengeTtlSeconds());
  }

  private String mfaSessionKey(String mfaSessionId) {
    return "mfa:session:" + mfaSessionId;
  }

  private String randomToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String formatRecoveryCode() {
    byte[] bytes = new byte[9];
    secureRandom.nextBytes(bytes);
    String token =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).toUpperCase(Locale.ROOT);
    token = token.replaceAll("[^A-Z0-9]", "");
    String raw = (token + "AAAAAAAAAAAA").substring(0, 12);
    return raw.substring(0, 4) + "-" + raw.substring(4, 8) + "-" + raw.substring(8, 12);
  }

  private <T> T readRedis(String key, Class<T> type) {
    String value = redisTemplate.opsForValue().get(key);
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(value, type);
    } catch (JsonProcessingException e) {
      throw new MfaException("MFA_SESSION_INVALID", HttpStatus.UNAUTHORIZED, "Invalid MFA state");
    }
  }

  private void saveRedis(String key, Object payload) {
    try {
      redisTemplate
          .opsForValue()
          .set(key, objectMapper.writeValueAsString(payload), Duration.ofSeconds(ttlSeconds()));
    } catch (JsonProcessingException e) {
      throw new MfaException(
          "MFA_SESSION_INVALID", HttpStatus.UNAUTHORIZED, "Failed to store MFA state");
    }
  }

  private record MfaSessionPayload(String userId, String expiresAt) {}

  private record ChallengePayload(String challenge, String expiresAt) {}
}
