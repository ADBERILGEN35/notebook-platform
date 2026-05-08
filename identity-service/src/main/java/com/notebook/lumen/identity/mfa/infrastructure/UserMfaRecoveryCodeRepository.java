package com.notebook.lumen.identity.mfa.infrastructure;

import com.notebook.lumen.identity.mfa.domain.UserMfaRecoveryCode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMfaRecoveryCodeRepository extends JpaRepository<UserMfaRecoveryCode, UUID> {
  List<UserMfaRecoveryCode> findByUserId(UUID userId);
}
