package com.notebook.lumen.identity.mfa.infrastructure;

import com.notebook.lumen.identity.mfa.domain.UserMfaSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMfaSettingsRepository extends JpaRepository<UserMfaSettings, UUID> {}
