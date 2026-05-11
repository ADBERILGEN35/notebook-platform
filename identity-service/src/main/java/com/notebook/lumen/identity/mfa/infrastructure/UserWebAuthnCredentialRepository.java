package com.notebook.lumen.identity.mfa.infrastructure;

import com.notebook.lumen.identity.mfa.domain.UserWebAuthnCredential;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserWebAuthnCredentialRepository
    extends JpaRepository<UserWebAuthnCredential, UUID> {
  List<UserWebAuthnCredential> findByUserId(UUID userId);

  java.util.Optional<UserWebAuthnCredential> findByCredentialId(String credentialId);

  List<UserWebAuthnCredential> findByUserIdAndRevokedAtIsNull(UUID userId);
}
