package com.notebook.lumen.identity.scim.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.notebook.lumen.identity.scim.ScimProperties;
import org.junit.jupiter.api.Test;

class ScimDeltaStrategyResolverTest {

  private final ScimDeltaStrategyResolver resolver = new ScimDeltaStrategyResolver();

  @Test
  void oktaSelectsLastModifiedWhenFilteringSupported() {
    var plan = resolver.resolve(properties("okta", true, true, true, true, true, true, true));

    assertThat(plan.selectedStrategy()).isEqualTo(ScimDeltaSyncStrategy.LAST_MODIFIED_FILTER);
    assertThat(plan.supportsFiltering()).isTrue();
    assertThat(plan.supportsPagination()).isTrue();
    assertThat(plan.warnings()).contains(ScimDeltaStrategyResolver.WARNING_MISSING_USER_IGNORED);
  }

  @Test
  void entraSelectsCursorCheckpointWhenFilteringSupported() {
    var plan = resolver.resolve(properties("azure-ad", true, true, true, true, true, true, true));

    assertThat(plan.selectedStrategy()).isEqualTo(ScimDeltaSyncStrategy.CURSOR_CHECKPOINT);
    assertThat(plan.providerType()).isEqualTo("azure-ad");
  }

  @Test
  void genericIsConservativeWhenPocEnabledWithoutDeltaSync() {
    var plan = resolver.resolve(properties("generic", true, false, false, true, true, true, false));

    assertThat(plan.selectedStrategy()).isEqualTo(ScimDeltaSyncStrategy.DISABLED);
    assertThat(plan.warnings()).contains(ScimDeltaStrategyResolver.WARNING_PROVIDER_UNSUPPORTED);
  }

  @Test
  void pocDisabledReturnsDisabledStrategy() {
    var plan = resolver.resolve(properties("okta", false, true, true, true, true, true, true));

    assertThat(plan.selectedStrategy()).isEqualTo(ScimDeltaSyncStrategy.DISABLED);
    assertThat(plan.deltaPocEnabled()).isFalse();
  }

  @Test
  void filteringUnavailableFallsBackForOkta() {
    var plan = resolver.resolve(properties("okta", true, true, false, true, true, true, true));

    assertThat(plan.selectedStrategy()).isEqualTo(ScimDeltaSyncStrategy.FULL_SYNC_FALLBACK);
    assertThat(plan.warnings()).contains(ScimDeltaStrategyResolver.WARNING_FILTERING_UNAVAILABLE);
    assertThat(plan.capabilityAligned()).isTrue();
  }

  @Test
  void deprovisionSemanticsNeverImplyMissingFromDelta() {
    assertThat(resolver.deprovisionSemantics(ScimDeltaProviderKind.OKTA)).contains("missing");
    assertThat(resolver.deprovisionSemantics(ScimDeltaProviderKind.AZURE_AD))
        .contains("not deletion");
    assertThat(resolver.deprovisionSemantics(ScimDeltaProviderKind.GENERIC))
        .contains("never deprovisions");
  }

  private static ScimProperties properties(
      String providerType,
      boolean pocEnabled,
      boolean deltaSyncEnabled,
      boolean filtering,
      boolean patch,
      boolean rateLimitAware,
      boolean dryRunOnly,
      boolean bulk) {
    return new ScimProperties(
        true,
        "token",
        "",
        true,
        "notebook-admins",
        true,
        5,
        bulk,
        100,
        10,
        providerType,
        deltaSyncEnabled,
        "diagnostic",
        bulk,
        filtering,
        patch,
        false,
        rateLimitAware,
        100,
        pocEnabled,
        dryRunOnly,
        false,
        3000,
        300,
        30,
        "",
        "",
        "",
        "",
        100,
        false,
        1,
        500,
        0);
  }
}
