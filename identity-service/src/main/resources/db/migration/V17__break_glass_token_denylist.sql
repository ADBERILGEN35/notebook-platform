alter table break_glass_access_events
  add column if not exists token_jti varchar(128),
  add column if not exists token_revoked_at timestamptz,
  add column if not exists token_revoked_by_user_id uuid,
  add column if not exists token_revocation_reason text;

create table if not exists break_glass_token_denylist (
  id uuid primary key,
  jti varchar(128) not null unique,
  session_id varchar(128) not null,
  event_id uuid,
  revoked_by_user_id uuid,
  revoked_at timestamptz not null,
  expires_at timestamptz not null,
  reason text not null,
  source varchar(64) not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_bg_token_denylist_expires_at on break_glass_token_denylist(expires_at);
create index if not exists idx_bg_token_denylist_session_id on break_glass_token_denylist(session_id);
