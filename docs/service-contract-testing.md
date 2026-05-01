# Service Contract Testing

Faz 17 locks the internal content-service to workspace-service contracts without introducing Spring
Cloud Contract or a broker.

## Contracts

Provider: `workspace-service`
Consumer: `content-service`

Endpoints:

- `GET /internal/notebooks/{notebookId}/permissions?userId={userId}`
- `GET /internal/workspaces/{workspaceId}/tags/{tagId}/exists?scope=NOTE`

`NotebookPermissionResponse` fields:

- `workspaceId`: UUID string
- `notebookId`: UUID string
- `role`: string
- `canRead`: boolean
- `canEdit`: boolean
- `canComment`: boolean
- `canManage`: boolean

`TagExistsResponse` fields:

- `workspaceId`: UUID string
- `tagId`: UUID string
- `scope`: string
- `exists`: boolean

## Test Approach

- Provider tests serialize workspace-service response records and assert required fields and JSON
  primitive types.
- Consumer tests use the real Spring HTTP Interface client against a lightweight in-process HTTP
  server and assert path, query and response compatibility.
- Snapshot validation rejects missing fields and wrong primitive types so silent client defaults are
  caught before runtime.

Test files:

- `workspace-service/src/test/java/com/notebook/lumen/workspace/api/InternalWorkspaceContractTest.java`
- `content-service/src/test/java/com/notebook/lumen/content/client/WorkspaceClientContractTest.java`

## Gaps

- No shared contract artifact is published yet.
- No OpenAPI diff or Pact broker exists.
- Auth behavior is covered by existing internal auth integration tests, not duplicated here.
- Backward-compatible additive fields are allowed; removing or changing existing fields should fail
  contract tests and require an explicit contract versioning decision.
