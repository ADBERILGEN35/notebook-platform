package com.notebook.lumen.identity.auth.application;

import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.auth.api.AuthResponse;
import com.notebook.lumen.identity.auth.api.LoginRequest;
import com.notebook.lumen.identity.auth.api.RefreshTokenRequest;
import com.notebook.lumen.identity.auth.api.SignupRequest;
import com.notebook.lumen.identity.shared.exception.EmailAlreadyExistsException;
import com.notebook.lumen.identity.shared.exception.InvalidCredentialsException;
import com.notebook.lumen.identity.shared.exception.InvalidRefreshTokenException;
import com.notebook.lumen.identity.shared.exception.UserDisabledException;
import com.notebook.lumen.identity.shared.exception.UserNotFoundException;
import com.notebook.lumen.identity.shared.exception.ValidationFailedException;
import com.notebook.lumen.identity.shared.security.EmailNormalizer;
import com.notebook.lumen.identity.shared.security.RefreshTokenHasher;
import com.notebook.lumen.identity.shared.security.jwt.JwtTokenService;
import com.notebook.lumen.identity.user.api.UserResponse;
import com.notebook.lumen.identity.user.domain.RefreshToken;
import com.notebook.lumen.identity.user.domain.User;
import com.notebook.lumen.identity.user.domain.UserStatus;
import com.notebook.lumen.identity.user.infrastructure.RefreshTokenRepository;
import com.notebook.lumen.identity.user.infrastructure.UserRepository;
import com.notebook.lumen.identity.user.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService jwtTokenService;
  private final UserMapper userMapper;
  private final AuditService auditService;

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenService jwtTokenService,
      UserMapper userMapper,
      AuditService auditService) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenService = jwtTokenService;
    this.userMapper = userMapper;
    this.auditService = auditService;
  }

  @Transactional
  public AuthResponse signup(SignupRequest request, HttpServletRequest httpRequest) {
    String email = normalizeEmailOrThrow(request.email());

    if (userRepository.findByEmail(email).isPresent()) {
      throw new EmailAlreadyExistsException(email);
    }

    validatePasswordPolicy(request.password());

    Instant now = Instant.now();
    UUID userId = UUID.randomUUID();
    String passwordHash = passwordEncoder.encode(request.password());

    User user =
        new User(
            userId,
            email,
            request.name(),
            request.avatarUrl(),
            passwordHash,
            UserStatus.ACTIVE,
            null,
            null,
            now,
            now,
            now,
            null);
    userRepository.save(user);
    auditService.record(
        "USER_SIGNED_UP",
        user.getId(),
        "USER",
        user.getId(),
        httpRequest,
        Map.of("status", "ACTIVE"));

    AuthResponse tokens = issueTokens(user, httpRequest);
    return tokens;
  }

  @Transactional
  public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
    String email = normalizeEmailOrThrow(request.email());

    Optional<User> found = userRepository.findByEmail(email);
    if (found.isEmpty()) {
      auditService.record(
          "USER_LOGIN_FAILED",
          null,
          "USER",
          null,
          httpRequest,
          Map.of("reason", "INVALID_CREDENTIALS"));
      throw new InvalidCredentialsException();
    }
    User user = found.get();

    if (user.getDeletedAt() != null || user.getStatus() == UserStatus.DELETED) {
      throw new UserDisabledException();
    }
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new UserDisabledException();
    }

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      auditService.record(
          "USER_LOGIN_FAILED",
          user.getId(),
          "USER",
          user.getId(),
          httpRequest,
          Map.of("reason", "INVALID_CREDENTIALS"));
      throw new InvalidCredentialsException();
    }

    user.setLastLoginAt(Instant.now());
    userRepository.save(user);
    auditService.record(
        "USER_LOGIN_SUCCEEDED", user.getId(), "USER", user.getId(), httpRequest, Map.of());

    return issueTokens(user, httpRequest);
  }

  @Transactional
  public AuthResponse refresh(RefreshTokenRequest request, HttpServletRequest httpRequest) {
    String refreshTokenPlaintext = request.refreshToken();
    if (refreshTokenPlaintext == null || refreshTokenPlaintext.isBlank()) {
      throw new ValidationFailedException("refreshToken is required");
    }

    JwtTokenService.RefreshTokenJwtClaims decoded =
        jwtTokenService.decodeRefreshToken(refreshTokenPlaintext);

    User user =
        userRepository
            .findById(decoded.userId())
            .orElseThrow(() -> new UserNotFoundException(decoded.userId()));

    if (user.getDeletedAt() != null
        || user.getStatus() == UserStatus.DELETED
        || user.getStatus() != UserStatus.ACTIVE) {
      throw new UserDisabledException();
    }

    String tokenHash = RefreshTokenHasher.hash(refreshTokenPlaintext);
    RefreshToken stored =
        refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(InvalidRefreshTokenException::new);

    if (!stored.getId().equals(decoded.refreshTokenId())) {
      throw new InvalidRefreshTokenException();
    }
    if (stored.getRevokedAt() != null) {
      // Reuse of revoked tokens is treated as invalid refresh.
      auditService.record(
          "REFRESH_TOKEN_REUSE_REJECTED",
          user.getId(),
          "REFRESH_TOKEN",
          stored.getId(),
          httpRequest,
          Map.of());
      throw new InvalidRefreshTokenException();
    }
    if (stored.getExpiresAt().isBefore(Instant.now())
        || stored.getExpiresAt().equals(Instant.now())) {
      throw new InvalidRefreshTokenException();
    }

    // Rotate refresh token.
    UUID newRefreshTokenId = UUID.randomUUID();
    Instant now = Instant.now();

    String newRefreshJwt = jwtTokenService.generateRefreshToken(user.getId(), newRefreshTokenId);
    String newRefreshHash = RefreshTokenHasher.hash(newRefreshJwt);
    Instant newExpiresAt = now.plusSeconds(jwtTokenService.refreshTokenTtlSeconds());

    RefreshToken newToken =
        new RefreshToken(
            newRefreshTokenId,
            user,
            newRefreshHash,
            newExpiresAt,
            null,
            null,
            now,
            extractClientIp(httpRequest),
            httpRequest.getHeader("User-Agent"));
    refreshTokenRepository.save(newToken);

    stored.revoke(now, newRefreshTokenId);
    refreshTokenRepository.save(stored);
    auditService.record(
        "REFRESH_TOKEN_ROTATED",
        user.getId(),
        "REFRESH_TOKEN",
        newRefreshTokenId,
        httpRequest,
        Map.of("replacedTokenId", stored.getId().toString()));

    String accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getEmail());
    long expiresIn = jwtTokenService.accessTokenTtlSeconds();

    UserResponse userResponse = userMapper.toResponse(user);
    return new AuthResponse(accessToken, newRefreshJwt, "Bearer", expiresIn, userResponse);
  }

  private AuthResponse issueTokens(User user, HttpServletRequest httpRequest) {
    UUID refreshTokenId = UUID.randomUUID();
    Instant now = Instant.now();

    String refreshJwt = jwtTokenService.generateRefreshToken(user.getId(), refreshTokenId);
    String refreshHash = RefreshTokenHasher.hash(refreshJwt);

    Instant expiresAt = now.plusSeconds(jwtTokenService.refreshTokenTtlSeconds());

    RefreshToken refreshToken =
        new RefreshToken(
            refreshTokenId,
            user,
            refreshHash,
            expiresAt,
            null,
            null,
            now,
            extractClientIp(httpRequest),
            httpRequest.getHeader("User-Agent"));
    refreshTokenRepository.save(refreshToken);

    String accessToken = jwtTokenService.generateAccessToken(user.getId(), user.getEmail());
    long expiresIn = jwtTokenService.accessTokenTtlSeconds();

    UserResponse userResponse = userMapper.toResponse(user);
    return new AuthResponse(accessToken, refreshJwt, "Bearer", expiresIn, userResponse);
  }

  private void validatePasswordPolicy(String password) {
    if (password == null) {
      throw new ValidationFailedException("Password is required");
    }
    boolean hasLetter = password.chars().anyMatch(Character::isLetter);
    boolean hasDigit = password.chars().anyMatch(Character::isDigit);
    if (password.length() < 10 || !hasLetter || !hasDigit) {
      throw new ValidationFailedException(
          "Password must be at least 10 chars and contain at least 1 letter and 1 digit");
    }
  }

  private String normalizeEmailOrThrow(String email) {
    String normalized = EmailNormalizer.normalize(email);
    if (normalized == null || normalized.isBlank()) {
      throw new ValidationFailedException("email is required");
    }
    return normalized;
  }

  private String extractClientIp(HttpServletRequest request) {
    String xf = request.getHeader("X-Forwarded-For");
    if (xf != null && !xf.isBlank()) {
      return xf.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
