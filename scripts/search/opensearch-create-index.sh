#!/usr/bin/env bash
set -euo pipefail

OPENSEARCH_URL="${OPENSEARCH_URL:?OPENSEARCH_URL is required}"
OPENSEARCH_INDEX_NOTES="${OPENSEARCH_INDEX_NOTES:-notebook-notes}"

auth_args=()
if [[ -n "${OPENSEARCH_USERNAME:-}" ]]; then
  auth_args=(-u "${OPENSEARCH_USERNAME}:${OPENSEARCH_PASSWORD:-}")
fi

curl -fsS "${auth_args[@]}" \
  -H "Content-Type: application/json" \
  -X PUT "${OPENSEARCH_URL%/}/${OPENSEARCH_INDEX_NOTES}" \
  --data-binary @- <<'JSON'
{
  "settings": {
    "analysis": {
      "analyzer": {
        "default": {
          "type": "standard"
        }
      }
    }
  },
  "mappings": {
    "dynamic": "strict",
    "properties": {
      "noteId": { "type": "keyword" },
      "workspaceId": { "type": "keyword" },
      "notebookId": { "type": "keyword" },
      "title": {
        "type": "text",
        "fields": {
          "keyword": { "type": "keyword", "ignore_above": 256 }
        }
      },
      "contentText": { "type": "text" },
      "tagsText": { "type": "text" },
      "notebookName": { "type": "text" },
      "createdBy": { "type": "keyword" },
      "updatedBy": { "type": "keyword" },
      "noteCreatedAt": { "type": "date" },
      "noteUpdatedAt": { "type": "date" },
      "archivedAt": { "type": "date" },
      "sourceVersion": { "type": "integer" },
      "indexedAt": { "type": "date" }
    }
  }
}
JSON
