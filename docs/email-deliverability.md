# Email Deliverability

Faz 33 defines the production-readiness standard before moving notification-service traffic to a real email provider.

## Sending Identity

- Use a dedicated transactional sending domain or subdomain such as `mail.example.com`.
- Keep marketing email on a separate provider or domain. Workspace invitations and security notifications are transactional.
- Use stable sender addresses:
  - Invitations: `no-reply@domain`
  - Security notifications: `security@domain`
- Configure `EMAIL_REPLY_TO` only when replies are monitored. Do not use a personal mailbox.
- Align the visible From domain, envelope sender / return-path domain and provider-verification domain where the provider supports it.

## SPF

SPF authorizes the email provider's sending infrastructure for the envelope sender domain. Use the provider's include value, not a guessed IP list.

## DKIM

DKIM signs outbound messages with provider-managed private keys and DNS-published public keys. Enable at least one selector before production traffic.

## DMARC

DMARC tells receiving domains how to evaluate SPF/DKIM alignment and where to send aggregate reports. Start with monitoring mode and only tighten after reports are clean:

1. `p=none`
2. `p=quarantine`
3. `p=reject`

Do not move directly to `p=reject`.

## Bounce and Complaint Handling

Provider webhooks must be enabled before production traffic. Bounces and complaints update notification lifecycle state and create active suppressions, preventing repeated sends to recipients that should not receive email.

## Production Checklist

- `EMAIL_PROVIDER` is `smtp`, `generic-http` or `sendgrid`; never `log` or `noop`.
- `SMTP_FROM` / `EMAIL_FROM` uses a verified transactional domain.
- `EMAIL_REPLY_TO` is either empty or monitored.
- SPF, DKIM and DMARC records are published using provider values.
- DMARC starts at `p=none` and has aggregate reporting enabled.
- `EMAIL_WEBHOOKS_ENABLED=true` only after `EMAIL_WEBHOOK_SECRET` and `EMAIL_WEBHOOK_REQUIRE_TIMESTAMP=true` are configured.
- Bounce and complaint webhook smoke tests pass.
- `scripts/email/provider-readiness-check.sh` passes in staging.
