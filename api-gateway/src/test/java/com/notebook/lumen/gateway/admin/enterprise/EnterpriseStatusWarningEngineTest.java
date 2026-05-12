package com.notebook.lumen.gateway.admin.enterprise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnterpriseStatusWarningEngineTest {

  private final EnterpriseStatusWarningEngine engine = new EnterpriseStatusWarningEngine();

  @Test
  void addsMfaWarningWhenNotEnforce() {
    var features =
        new EnterpriseStatusFeatures(
            new SsoStatus(false, 0, false, false, false),
            scimStatus(),
            new MfaStatus("warn", List.of("webauthn"), true, true),
            new SiemStatus(false, "noop", false, false, false),
            new AdminRbacStatus(false, true, Map.of()),
            new BreakGlassStatus(
                false,
                "static-token",
                List.of(),
                false,
                false,
                0,
                false,
                false,
                false,
                null,
                "disabled",
                0,
                0,
                false,
                false,
                15,
                1,
                true,
                true,
                false,
                false,
                0,
                null,
                null),
            new AuditExportStatus(false, false, false, false, "", false),
            new NotificationsStatus(false, false, false, false),
            new GatewaySecurityStatus(true, true, "warn", List.of(), true, false, "bearer", false),
            new MergeResolutionStatus(false, false, List.of(1), true, true, false));

    var warnings = engine.build(features, false, false, false);
    assertThat(warnings.stream().map(EnterpriseWarning::code))
        .contains("ADMIN_MFA_MODE_NOT_ENFORCE");
  }

  @Test
  void flagsSsoWithoutAdminMapping() {
    var features =
        new EnterpriseStatusFeatures(
            new SsoStatus(true, 1, false, false, false),
            scimStatus(),
            new MfaStatus("enforce", List.of("webauthn"), true, true),
            new SiemStatus(false, "noop", false, false, false),
            new AdminRbacStatus(false, true, Map.of()),
            new BreakGlassStatus(
                false,
                "static-token",
                List.of(),
                false,
                false,
                0,
                false,
                false,
                false,
                null,
                "disabled",
                0,
                0,
                false,
                false,
                15,
                1,
                true,
                true,
                false,
                false,
                0,
                null,
                null),
            new AuditExportStatus(false, false, false, false, "", false),
            new NotificationsStatus(true, false, true, true),
            new GatewaySecurityStatus(
                true, true, "enforce", List.of(), true, false, "bearer", false),
            new MergeResolutionStatus(false, false, List.of(1), true, true, false));

    var warnings = engine.build(features, false, false, false);
    assertThat(warnings.stream().map(EnterpriseWarning::code))
        .contains("SSO_ADMIN_MAPPING_MISSING");
  }

  private static ScimStatus scimStatus() {
    return new ScimStatus(
        false,
        false,
        false,
        false,
        "generic",
        false,
        "disabled",
        false,
        true,
        true,
        false,
        true,
        100);
  }
}
