package com.notebook.lumen.identity.admin.retention;

public record RetentionTargetDefinition(
    String targetKey,
    String service,
    String displayName,
    String description,
    RetentionDataClass dataClass,
    Integer defaultRetentionDays,
    boolean legalHoldSupported,
    boolean destructivePurgeSupported,
    boolean dryRunSupported,
    boolean archiveRequiredBeforePurge,
    RetentionRiskLevel riskLevel,
    RetentionTargetStatus status) {}
