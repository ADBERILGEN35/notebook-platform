package com.notebook.lumen.identity.admin.changerequest;

import java.util.List;
import java.util.Set;

public record AdminOperationDefinition(
    String operationType,
    String targetService,
    String targetKey,
    Set<String> allowedNormalizedValues,
    String severity,
    String requiredCreatePermission,
    boolean requiresApproval,
    boolean runtimeApplySupported,
    String rollbackHint,
    List<String> affectedSurfaces,
    String description) {

  public boolean isValueAllowed(String normalizedValue) {
    return allowedNormalizedValues.contains(normalizedValue);
  }
}
