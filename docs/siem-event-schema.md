# SIEM Event Schema (Faz 62)

Schema version: `1`

```json
{
  "id": "uuid",
  "timestamp": "2026-05-08T14:00:00Z",
  "sourceService": "identity-service",
  "environment": "staging",
  "eventType": "SCIM_USER_DEPROVISIONED",
  "category": "SCIM",
  "severity": "HIGH",
  "actorUserId": "uuid-or-null",
  "subjectUserId": "uuid-or-null",
  "workspaceId": null,
  "requestId": "req-123",
  "ipAddress": "10.0.0.1",
  "userAgent": "agent",
  "metadata": {
    "reason": "active=false"
  },
  "schemaVersion": 1
}
```

## Guvenlik Kurallari

- Token/secret/cookie/private credential alanlari sanitize edilir.
- Ham OAuth/SCIM/SIEM token payload'a dahil edilmez.
- Full request body stream edilmez.
- Metadata minimum gerekli alanlarla tutulur.
