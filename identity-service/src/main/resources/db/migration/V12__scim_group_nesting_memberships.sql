-- Faz 76: SCIM group nesting + normalized group memberships

ALTER TABLE scim_groups DROP CONSTRAINT IF EXISTS scim_groups_external_id_key;

ALTER TABLE scim_groups ALTER COLUMN external_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_scim_groups_external_id
  ON scim_groups(external_id)
  WHERE external_id IS NOT NULL;

ALTER TABLE scim_groups ADD COLUMN IF NOT EXISTS provider VARCHAR(255);
ALTER TABLE scim_groups ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true;

CREATE TABLE IF NOT EXISTS scim_group_memberships (
  id UUID PRIMARY KEY,
  group_id UUID NOT NULL REFERENCES scim_groups(id) ON DELETE CASCADE,
  member_type VARCHAR(10) NOT NULL CHECK (member_type IN ('USER', 'GROUP')),
  member_user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  member_group_id UUID REFERENCES scim_groups(id) ON DELETE CASCADE,
  member_external_id VARCHAR(255),
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT chk_scim_group_member_user_shape CHECK (
    (member_type = 'USER' AND member_user_id IS NOT NULL AND member_group_id IS NULL)
    OR (member_type = 'GROUP' AND member_group_id IS NOT NULL AND member_user_id IS NULL)
  ),
  CONSTRAINT chk_scim_group_not_self_group CHECK (
    member_type <> 'GROUP' OR group_id <> member_group_id
  )
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_scim_group_membership_user
  ON scim_group_memberships(group_id, member_user_id)
  WHERE member_type = 'USER' AND member_user_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_scim_group_membership_nested_group
  ON scim_group_memberships(group_id, member_group_id)
  WHERE member_type = 'GROUP' AND member_group_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_scim_group_memberships_group_id ON scim_group_memberships(group_id);
CREATE INDEX IF NOT EXISTS ix_scim_group_memberships_member_user ON scim_group_memberships(member_user_id);
CREATE INDEX IF NOT EXISTS ix_scim_group_memberships_member_group ON scim_group_memberships(member_group_id);

-- Ensure every legacy user membership has a scim_groups row
INSERT INTO scim_groups (id, external_id, display_name, platform_role, created_at, updated_at, active, provider)
SELECT gen_random_uuid(), u.group_external_id, MAX(u.group_display_name), NULL, MIN(u.created_at), NOW(), true, NULL
FROM user_scim_group_memberships u
WHERE NOT EXISTS (SELECT 1 FROM scim_groups sg WHERE sg.external_id = u.group_external_id)
GROUP BY u.group_external_id;

INSERT INTO scim_group_memberships (id, group_id, member_type, member_user_id, member_group_id, member_external_id, created_at)
SELECT gen_random_uuid(), sg.id, 'USER', usgm.user_id, NULL, NULL, usgm.created_at
FROM user_scim_group_memberships usgm
JOIN scim_groups sg ON sg.external_id = usgm.group_external_id;

DROP TABLE IF EXISTS user_scim_group_memberships;
