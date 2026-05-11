package com.notebook.lumen.identity.admin.rbac.overrides;

import java.util.List;

public record AdminRbacOverrideParseResult(
    List<AdminRbacOverrideAssignmentRow> acceptedAssignments,
    List<String> warnings,
    List<String> errors,
    int validAssignmentCount,
    int ignoredAssignmentCount,
    String manifestVersion) {}
