# Break-glass static-token rotation runbook (Faz 94)

This runbook covers the operator flow after the emergency break-glass static
token has been used. It assumes Faz 90–93 are in place (event log, denylist,
revocation) and Faz 94 rotation governance is enabled
(`BREAK_GLASS_STATIC_TOKEN_ROTATION_TRACKING_ENABLED=true`,
`BREAK_GLASS_ROTATION_API_ENABLED=true`).

The runbook does *not* require any new token material to be entered into the
admin console. Token material flows only through the existing External Secret
/ GitOps pipeline.

## 1. Detection

When a break-glass static-token login succeeds and
`BREAK_GLASS_STATIC_TOKEN_ROTATION_TRACKING_ENABLED=true`:

- A `BREAK_GLASS_TOKEN_ROTATION_REQUIRED` audit event is emitted.
- A row appears in `break_glass_token_rotation_events` with status `REQUIRED`.
- The status endpoint reports `rotationRequired: true` and `openRotationEvents >= 1`.
- The admin console shows the event in **Admin → Break-glass rotation**.

Pages / SIEM should fire on `BREAK_GLASS_TOKEN_ROTATION_REQUIRED`.

## 2. Acknowledge

A security admin (permission `admin:break-glass:rotation:manage`, MFA verified)
opens the rotation page and clicks **Acknowledge** with a reason such as:

> "Security team paged. Starting External Secret rotation procedure."

This transitions the event to `ACKNOWLEDGED` and records the actor. It does not
modify the token or any secret.

## 3. Rotate the External Secret

Out-of-band, in the secrets backend:

1. Generate a new random token. Hash it with SHA-256 in the expected format
   (`sha256:<hex>`).
2. Update the External Secret (e.g. AWS Secrets Manager, Vault) that backs
   `BREAK_GLASS_TOKEN_HASH`.
3. Trigger the GitOps reconciliation or wait for the controller to project the
   new value into the cluster secret.
4. Roll the identity-service pods so the new value is loaded:
   `kubectl rollout restart deploy/identity-service -n notebook`.

The plaintext token must be stored in the offline break-glass safe per the
existing break-glass-admin-access runbook.

## 4. Verify

Once the pods are up with the new configuration, return to the rotation page
and click **Verify** with a reason such as:

> "External Secret updated and identity pods restarted (rev 2026-05-11/1)."

Backend behavior:

- identity-service computes `fp:sha256(current BREAK_GLASS_TOKEN_HASH)`.
- If different from `oldTokenHashFingerprint`, status becomes `VERIFIED` and
  the new fingerprint is persisted on the event.
- If still equal, the API returns `BREAK_GLASS_ROTATION_NOT_CHANGED` (409) and
  audits `BREAK_GLASS_TOKEN_ROTATION_VERIFY_FAILED`. The pod did not pick up
  the new value or the External Secret was not actually rotated — investigate
  before retrying.

## 5. Close

After incident review (Faz 92 review of the break-glass event itself, SIEM
correlation, etc.) finishes, click **Close** with a reason:

> "Rotation verified, incident #INC-... reviewed, no follow-up actions."

`Close` is only allowed from `VERIFIED`. `REQUIRED → CLOSED` and
`ACKNOWLEDGED → CLOSED` are rejected with `BREAK_GLASS_ROTATION_INVALID_TRANSITION`.

## 6. SIEM / audit confirmation

Confirm the SIEM dashboard shows the full chain for this rotation key:

- `BREAK_GLASS_STATIC_TOKEN_ROTATION_REQUIRED`
- `BREAK_GLASS_TOKEN_ROTATION_REQUIRED`
- `BREAK_GLASS_TOKEN_ROTATION_ACKNOWLEDGED`
- `BREAK_GLASS_TOKEN_ROTATION_VERIFIED`
- `BREAK_GLASS_TOKEN_ROTATION_CLOSED`

## Failure modes and what to do

| Symptom | Cause | Action |
| --- | --- | --- |
| Verify keeps returning `BREAK_GLASS_ROTATION_NOT_CHANGED` | External Secret update not propagated or pods not restarted | Re-run rollout, check ExternalSecret reconcile, then retry |
| `BREAK_GLASS_ROTATION_LIMIT_REACHED` on next use | Too many open REQUIRED+ACKNOWLEDGED events | Close older verified ones, then retry; raise the cap only as a last resort |
| `BREAK_GLASS_ROTATION_API_DISABLED` | API flag off | Enable `BREAK_GLASS_ROTATION_API_ENABLED` via GitOps PR |
| `admin:break-glass:rotation:manage` denied | Operator lacks permission | Add a temporary role assignment via the admin RBAC flow |

## What NOT to do

- Do **not** paste a new token into the admin console or any chat. The console
  has no input for it.
- Do **not** edit `break_glass_token_rotation_events` rows directly to force
  `VERIFIED` status; the verification check exists to catch missed runbook steps.
- Do **not** disable rotation tracking to clear noisy events; close them.
