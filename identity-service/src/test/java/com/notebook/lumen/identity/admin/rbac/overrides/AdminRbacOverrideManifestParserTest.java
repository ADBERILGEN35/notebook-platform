package com.notebook.lumen.identity.admin.rbac.overrides;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.identity.user.infrastructure.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminRbacOverrideManifestParserTest {

  @Mock UserRepository userRepository;

  AdminRbacOverrideManifestParser parser;
  AdminRbacOverridesProperties props =
      new AdminRbacOverridesProperties(
          false, "/tmp/x.yaml", false, 500, true, false, true, false, false, 30, 5, false);

  @BeforeEach
  void setUp() {
    parser = new AdminRbacOverrideManifestParser(userRepository);
  }

  @Test
  void parsesApprovedGrant() {
    UUID u = UUID.randomUUID();
    when(userRepository.existsById(u)).thenReturn(true);
    String yaml =
        "adminRbacOverrides:\n  version: 1\n  assignments:\n    - userId: "
            + u
            + "\n      role: "
            + PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER
            + "\n      action: GRANT\n      reasonRef: change-request:abc\n      requestedBy: \"\"\n      approvedBy: \"\"\n      status: APPROVED_FOR_APPLY\n      expiresAt: null\n      createdAt: \"2026-01-01T00:00:00Z\"\n      metadata:\n        source: gitops\n";
    AdminRbacOverrideParseResult r = parser.parse(yaml, props, true);
    assertThat(r.errors()).isEmpty();
    assertThat(r.acceptedAssignments()).hasSize(1);
    assertThat(r.acceptedAssignments().get(0).userId()).isEqualTo(u);
    assertThat(r.manifestVersion()).isEqualTo("1");
  }

  @Test
  void disabledStatusSkipped() {
    UUID u = UUID.randomUUID();
    String yaml =
        "adminRbacOverrides:\n  version: 1\n  assignments:\n    - userId: "
            + u
            + "\n      role: "
            + PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER
            + "\n      action: GRANT\n      reasonRef: change-request:abc\n      status: DISABLED\n";
    AdminRbacOverrideParseResult r = parser.parse(yaml, props, true);
    assertThat(r.acceptedAssignments()).isEmpty();
  }
}
