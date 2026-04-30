# Backup And Restore

## Databases

The current compose setup uses one PostgreSQL instance, but the service boundary should be treated as separate logical databases for production planning:

- `identity-db`: users and refresh token hashes
- `workspace-db`: workspaces, memberships, notebooks, tags, invitations
- `content-db`: notes, immutable note versions, links, comments, note tags

Production can keep these as separate databases or separate schemas, but backup and restore should preserve service boundaries.

## Backup Frequency

- Identity: at least hourly; contains user accounts and refresh token state.
- Workspace: at least hourly; contains authorization state and invitations.
- Content: at least hourly or more frequent depending on note write volume.
- Retention: daily backups for 30 days, weekly backups for 12 weeks as a starting point.

## pg_dump Examples

```bash
pg_dump --format=custom --dbname="$IDENTITY_DB_URL" --file=identity-$(date +%F-%H%M).dump
pg_dump --format=custom --dbname="$WORKSPACE_DB_URL" --file=workspace-$(date +%F-%H%M).dump
pg_dump --format=custom --dbname="$CONTENT_DB_URL" --file=content-$(date +%F-%H%M).dump
```

For the local single database compose setup:

```bash
docker compose exec postgres pg_dump -U notebook -d notebook_platform --format=custom --file=/tmp/notebook-platform.dump
docker compose cp postgres:/tmp/notebook-platform.dump ./notebook-platform.dump
```

## Restore Examples

Restore into empty target databases first, then start services:

```bash
pg_restore --clean --if-exists --dbname="$IDENTITY_DB_URL" identity.dump
pg_restore --clean --if-exists --dbname="$WORKSPACE_DB_URL" workspace.dump
pg_restore --clean --if-exists --dbname="$CONTENT_DB_URL" content.dump
```

For local compose:

```bash
docker compose cp ./notebook-platform.dump postgres:/tmp/notebook-platform.dump
docker compose exec postgres pg_restore --clean --if-exists -U notebook -d notebook_platform /tmp/notebook-platform.dump
```

## Migration Restore Order

1. Restore database backups into the target environment.
2. Start services with Flyway enabled.
3. Flyway should validate schema history before applying newer migrations.
4. Do not manually edit existing migration files; add forward migrations only.

## PITR Notes

For production PostgreSQL, enable WAL archiving and point-in-time recovery. Content writes can grow quickly because `note_versions` is immutable history; WAL volume and backup windows should be sized with that in mind.

## Sensitive Data Notes

- Identity backups contain emails, password hashes and refresh token hashes.
- Workspace backups contain invitation token hashes and membership data.
- Content backups contain user content in JSONB, comments and note history.

Encrypt backups at rest and restrict restore access. Treat backup files as production secrets.
