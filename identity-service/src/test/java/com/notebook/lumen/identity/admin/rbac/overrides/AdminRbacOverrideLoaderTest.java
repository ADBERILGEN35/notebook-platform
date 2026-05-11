package com.notebook.lumen.identity.admin.rbac.overrides;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.notebook.lumen.common.security.admin.PlatformAdminRbacConstants;
import com.notebook.lumen.identity.audit.AuditService;
import com.notebook.lumen.identity.user.infrastructure.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class AdminRbacOverrideLoaderTest {

  @Mock UserRepository userRepository;
  @Mock AuditService auditService;

  @Test
  void reloadInvalidKeepsLastKnownGood(@TempDir Path tempDir) throws Exception {
    UUID u = UUID.randomUUID();
    when(userRepository.existsById(u)).thenReturn(true);
    Path f = tempDir.resolve("admin-rbac-overrides.yaml");
    String good =
        "adminRbacOverrides:\n  version: 1\n  assignments:\n    - userId: "
            + u
            + "\n      role: "
            + PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER
            + "\n      action: GRANT\n      reasonRef: change-request:abc\n      requestedBy: \"\"\n      approvedBy: \"\"\n      status: APPROVED_FOR_APPLY\n      expiresAt: null\n      createdAt: \"2026-01-01T00:00:00Z\"\n      metadata:\n        source: gitops\n";
    Files.writeString(f, good);
    AdminRbacOverridesProperties props =
        new AdminRbacOverridesProperties(
            true, f.toString(), false, 500, true, true, true, false, false, 30, 5, false);
    AdminRbacOverrideManifestParser parser = new AdminRbacOverrideManifestParser(userRepository);
    AdminRbacOverrideLoader loader = newLoader(props, parser);
    loader.bootstrapFromDisk(null);
    assertThat(loader.snapshot().loaded()).isTrue();
    String checksumBefore = loader.snapshot().checksum();

    // Parser errors (not just warnings) so reload fails and last-known-good applies.
    Files.writeString(f, "notAnOverrideRoot: true\n");
    var resp =
        loader.reload(UUID.randomUUID(), "operator reload after bad gitops sync for incident.");
    assertThat(resp.result()).isEqualTo("FAILED");
    assertThat(loader.snapshot().loaded()).isTrue();
    assertThat(loader.snapshot().checksum()).isEqualTo(checksumBefore);

    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    verify(auditService, atLeastOnce()).record(captor.capture(), any(), any(), any(), any(), any());
    assertThat(captor.getAllValues())
        .contains(
            "ADMIN_RBAC_OVERRIDES_LAST_KNOWN_GOOD_USED", "ADMIN_RBAC_OVERRIDES_RELOAD_FAILED");
  }

  @Test
  void reloadDisabledThrows(@TempDir Path tempDir) throws Exception {
    UUID u = UUID.randomUUID();
    when(userRepository.existsById(u)).thenReturn(true);
    Path f = tempDir.resolve("x.yaml");
    String good =
        "adminRbacOverrides:\n  version: 1\n  assignments:\n    - userId: "
            + u
            + "\n      role: "
            + PlatformAdminRbacConstants.ROLE_PLATFORM_AUDIT_VIEWER
            + "\n      action: GRANT\n      reasonRef: change-request:abc\n      requestedBy: \"\"\n      approvedBy: \"\"\n      status: APPROVED_FOR_APPLY\n      expiresAt: null\n      createdAt: \"2026-01-01T00:00:00Z\"\n      metadata:\n        source: gitops\n";
    Files.writeString(f, good);
    AdminRbacOverridesProperties props =
        new AdminRbacOverridesProperties(
            true, f.toString(), false, 500, true, false, true, false, false, 30, 5, false);
    AdminRbacOverrideManifestParser parser = new AdminRbacOverrideManifestParser(userRepository);
    AdminRbacOverrideLoader loader = newLoader(props, parser);
    loader.bootstrapFromDisk(null);
    assertThatThrownBy(() -> loader.reload(UUID.randomUUID(), "operator needs reload after deploy."))
        .isInstanceOf(AdminRbacOverridesReloadException.class);
  }

  @SuppressWarnings("unchecked")
  private AdminRbacOverrideLoader newLoader(
      AdminRbacOverridesProperties props, AdminRbacOverrideManifestParser parser) {
    SimpleMeterRegistry reg = new SimpleMeterRegistry();
    ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(reg);
    return new AdminRbacOverrideLoader(props, parser, auditService, provider);
  }
}
