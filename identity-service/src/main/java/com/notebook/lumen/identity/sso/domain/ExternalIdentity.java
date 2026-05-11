package com.notebook.lumen.identity.sso.domain;

import com.notebook.lumen.identity.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "external_identities")
public class ExternalIdentity {

  @Id
  @Column(name = "id", nullable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "provider", nullable = false, length = 100)
  private String provider;

  @Column(name = "subject", nullable = false, length = 255)
  private String subject;

  @Column(name = "email", nullable = false, length = 320)
  private String email;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @Column(name = "claims")
  @JdbcTypeCode(SqlTypes.JSON)
  private String claims;

  @Column(name = "linked_at", nullable = false)
  private Instant linkedAt;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  protected ExternalIdentity() {}

  public ExternalIdentity(
      UUID id,
      User user,
      String provider,
      String subject,
      String email,
      boolean emailVerified,
      String claims,
      Instant linkedAt,
      Instant lastLoginAt) {
    this.id = id;
    this.user = user;
    this.provider = provider;
    this.subject = subject;
    this.email = email;
    this.emailVerified = emailVerified;
    this.claims = claims;
    this.linkedAt = linkedAt;
    this.lastLoginAt = lastLoginAt;
  }

  public UUID getId() {
    return id;
  }

  public User getUser() {
    return user;
  }

  public String getProvider() {
    return provider;
  }

  public String getSubject() {
    return subject;
  }

  public String getEmail() {
    return email;
  }

  public boolean isEmailVerified() {
    return emailVerified;
  }

  public String getClaims() {
    return claims;
  }

  public Instant getLinkedAt() {
    return linkedAt;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public void markLogin(Instant now, String email, boolean verified, String claims) {
    this.lastLoginAt = now;
    this.email = email;
    this.emailVerified = verified;
    this.claims = claims;
  }
}
