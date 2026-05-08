# Generic HTTP SIEM Provider (Faz 62)

`SIEM_PROVIDER=generic-http` secimi ile outbox batch NDJSON olarak SIEM endpointine gonderilir.

## Request

- Method: `POST`
- Content-Type: `application/x-ndjson`
- Body: her satir bir JSON event

## Auth modlari

- `SIEM_AUTH_MODE=none`
- `SIEM_AUTH_MODE=bearer` + `SIEM_BEARER_TOKEN`
- `SIEM_AUTH_MODE=header` + `SIEM_CUSTOM_HEADER_NAME` + `SIEM_CUSTOM_HEADER_VALUE`

## Response handling

- `2xx` => outbox `SENT`
- `429` veya `5xx` => retryable
- diger `4xx` => `DEAD`

## Provider mapping notlari

- Splunk HEC: endpoint URL ve custom header/bearer ile baglanabilir.
- Elastic ingest endpoint: NDJSON pipeline ile baglanabilir.
- Sentinel custom connector: HTTP collector ile ayni payload maplenebilir.

Vendor SDK entegrasyonlari Faz 62 kapsam disidir.
