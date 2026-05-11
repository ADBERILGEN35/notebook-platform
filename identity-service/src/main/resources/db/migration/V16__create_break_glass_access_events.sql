create table if not exists break_glass_access_events (
  id uuid primary key,
  session_id varchar(128) not null unique,
  mode varchar(64) not null,
  reason_hash varchar(128),
  reason_summary varchar(256),
  actor_label varchar(128) not null,
  status varchar(64) not null,
  issued_at timestamptz not null,
  expires_at timestamptz not null,
  reviewed_by_user_id uuid,
  reviewed_at timestamptz,
  review_decision varchar(32),
  review_reason text,
  source_ip_hash varchar(128),
  user_agent_hash varchar(128),
  rotation_required boolean not null default false,
  notification_sent boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_break_glass_access_events_status_issued_at
  on break_glass_access_events(status, issued_at desc);
