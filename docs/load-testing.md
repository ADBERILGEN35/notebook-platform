# Load Testing

Load tests are provided as k6 scripts. They are not run in CI because they create data and require a running backend stack.

## Scripts

- `scripts/k6/backend-smoke-load.js`: signup/login, workspace/notebook setup, note create/update/search/comment.
- `scripts/k6/auth-login-load.js`: repeated login against an existing test user.
- `scripts/k6/content-write-load.js`: note create/update using pre-created workspace/notebook/token.

## Local Run

Start the stack:

```bash
docker compose up --build
```

Run a small backend smoke load:

```bash
BASE_URL=http://localhost:8080 VUS=2 DURATION=30s k6 run scripts/k6/backend-smoke-load.js
```

Run login load with a known user:

```bash
BASE_URL=http://localhost:8080 TEST_EMAIL=user@example.com TEST_PASSWORD='StrongerPass123!' VUS=5 DURATION=1m k6 run scripts/k6/auth-login-load.js
```

Run content write load with prepared identifiers:

```bash
BASE_URL=http://localhost:8080 \
ACCESS_TOKEN=<token> \
WORKSPACE_ID=<workspaceId> \
NOTEBOOK_ID=<notebookId> \
VUS=3 DURATION=1m \
k6 run scripts/k6/content-write-load.js
```

## Safety Notes

- Do not run these against production without an approved test window.
- Use dedicated staging data and credentials.
- Clean up generated workspaces/notes after long runs.
- Watch PostgreSQL disk growth; `note_versions` is immutable and write-heavy tests create history quickly.
