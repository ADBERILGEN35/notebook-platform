create table if not exists break_glass_token_rotation_events (
  id uuid primary key,
  rotation_key varchar(128) not null unique,
  credential_mode varchar(64) not null,
  status varchar(32) not null,
  triggered_by_event_id uuid,
  triggered_by_session_id varchar(128),
  old_token_hash_fingerprint varchar(64),
  new_token_hash_fingerprint varchar(64),
  required_at timestamptz not null,
  acknowledged_at timestamptz,
  acknowledged_by_user_id uuid,
  verified_at timestamptz,
  verified_by_user_id uuid,
  closed_at timestamptz,
  reason text,
  created_at timestamptz not null,
  updated_at timestamptz not null
);

create index if not exists idx_bg_rotation_status on break_glass_token_rotation_events(status);
create index if not exists idx_bg_rotation_required_at on break_glass_token_rotation_events(required_at);
create index if not exists idx_bg_rotation_old_fp on break_glass_token_rotation_events(old_token_hash_fingerprint);
