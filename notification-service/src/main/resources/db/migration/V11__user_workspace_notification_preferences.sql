CREATE TABLE user_workspace_notification_preferences (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    notification_type VARCHAR(100) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX ux_user_workspace_notification_prefs
    ON user_workspace_notification_preferences (user_id, workspace_id, notification_type, channel);

CREATE INDEX idx_user_workspace_notification_prefs_workspace
    ON user_workspace_notification_preferences (workspace_id, user_id);
