# Audit Retention

Audit events are security and compliance data. Faz 23 documents retention and adds an example purge
SQL, but does not introduce automatic deletion.

## Recommended Retention

| Stream | Recommended minimum | Notes |
|---|---:|---|
| identity audit events | 365 days | Authentication and refresh-token security events are high value for incidents. |
| workspace audit events | 180-365 days | Workspace membership, invitation and notebook operations. |
| content audit events | 180-365 days | Note/comment/tag activity, depending on compliance needs. |

Security-sensitive events such as refresh token reuse, revoke-all, suspicious authentication and
privilege changes may need longer retention based on legal/compliance policy.

## Purge Policy

Before deletion:

1. Export/archive events to a controlled destination.
2. Verify legal hold requirements.
3. Run purge in staging.
4. Run with a narrow date cutoff.
5. Record the purge as an operational change.

Automatic scheduled deletion is intentionally not implemented in this phase because production
retention depends on legal/compliance requirements and archive readiness.

## Future Work

- SIEM streaming.
- Object storage archive.
- CSV/JSON export job.
- WORM/immutable storage.
- Per-event-type retention classes.
