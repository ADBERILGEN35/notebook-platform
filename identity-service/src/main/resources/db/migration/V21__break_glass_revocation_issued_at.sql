alter table break_glass_token_denylist
  add column if not exists issued_at timestamptz;

update break_glass_token_denylist d
set issued_at = coalesce(
  (select e.issued_at from break_glass_access_events e where e.id = d.event_id),
  d.revoked_at)
where issued_at is null;

update break_glass_token_denylist
set issued_at = revoked_at
where issued_at is null;

alter table break_glass_token_denylist
  alter column issued_at set not null;
