# Email DNS Records

These templates are provider-agnostic placeholders. Replace every provider value with the exact values shown by the email provider. Do not commit real secrets or account-specific DNS values.

## SPF

```text
@ TXT "v=spf1 include:<provider-spf-domain> -all"
```

Use one SPF TXT record per domain. If a record already exists, merge the provider include into the existing record instead of creating a second SPF record.

## DKIM

```text
<selector>._domainkey TXT "v=DKIM1; k=rsa; p=<provider-public-key>"
```

Some providers use CNAME records instead of TXT records:

```text
<selector>._domainkey CNAME <provider-dkim-target>
```

Use the provider's exact selector and target.

## DMARC

Start with monitoring:

```text
_dmarc TXT "v=DMARC1; p=none; rua=mailto:dmarc-reports@domain; ruf=mailto:dmarc-forensic@domain; fo=1"
```

Roll out gradually:

```text
_dmarc TXT "v=DMARC1; p=quarantine; rua=mailto:dmarc-reports@domain; pct=25"
_dmarc TXT "v=DMARC1; p=reject; rua=mailto:dmarc-reports@domain"
```

Move to `quarantine` and `reject` only after aggregate reports show that legitimate mail aligns with SPF or DKIM.

## Verification Notes

- Provider dashboards are the source of truth for domain verification status.
- `scripts/email/provider-readiness-check.sh` can attempt best-effort SPF/DMARC lookups when `dig` or `nslookup` is installed.
- DKIM verification is provider-specific and should be checked in the provider console.
