package com.notebook.lumen.workspace.shared;

import java.util.UUID;

public record UserContext(UUID userId, String email, UUID workspaceId) {}
