CREATE TABLE IF NOT EXISTS scim_groups (
  id UUID PRIMARY KEY,
  external_id VARCHAR(255) NOT NULL UNIQUE,
  display_name VARCHAR(255) NOT NULL,
  platform_role VARCHAR(80),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS user_scim_group_memberships (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES users(id),
  group_external_id VARCHAR(255) NOT NULL,
  group_display_name VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_user_scim_group_membership
  ON user_scim_group_memberships(user_id, group_external_id);

CREATE INDEX IF NOT EXISTS ix_user_scim_group_memberships_user_id
  ON user_scim_group_memberships(user_id);
