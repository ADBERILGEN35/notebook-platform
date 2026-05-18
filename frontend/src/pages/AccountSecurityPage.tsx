import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { logout, revokeAll } from '../features/auth/auth-api'
import { getMfaSettings, generateRecoveryCodes, registrationOptions, registrationVerify } from '../features/auth/mfa-api'
import { useAuthStore } from '../features/auth/auth-store'
import { isMfaUiEnabled } from '../shared/config/notifications-feature-flags'
import { isWebAuthnSupported } from '../shared/security/webauthn-support'
import { isCookieMode } from '../shared/config/auth-transport'
import { PageHeader } from '../shared/components/PageHeader'
import { SectionCard } from '../shared/components/SectionCard'
import { Button } from '../shared/components/Button'
import { Input } from '../shared/components/Input'
import { ErrorState } from '../shared/components/ErrorState'
import { ConfirmActionModal } from '../shared/components/ConfirmActionModal'
import { SecurityMethodCard } from '../features/settings/SecurityMethodCard'
import { SessionRow } from '../features/settings/SessionRow'

export function AccountSecurityPage() {
  const navigate = useNavigate()
  const refreshToken = useAuthStore((s) => s.refreshToken)
  const clearSession = useAuthStore((s) => s.clearSession)
  const mfaUiEnabled = isMfaUiEnabled()
  const webAuthnSupported = isWebAuthnSupported()
  const [passkeyName, setPasskeyName] = useState('')
  const [recoveryCodes, setRecoveryCodes] = useState<string[] | null>(null)
  const [ackSavedCodes, setAckSavedCodes] = useState(false)
  const [confirmRevokeAll, setConfirmRevokeAll] = useState(false)

  const mfaSettingsQuery = useQuery({
    queryKey: ['mfa-settings'],
    queryFn: getMfaSettings,
    enabled: mfaUiEnabled,
  })

  const logoutMutation = useMutation({
    mutationFn: () => logout(isCookieMode() ? null : refreshToken),
    onSuccess: () => {
      clearSession()
      navigate('/login', { replace: true })
    },
  })

  const revokeMutation = useMutation({
    mutationFn: revokeAll,
    onSuccess: () => setConfirmRevokeAll(false),
  })

  const setupPasskeyMutation = useMutation({
    mutationFn: async () => {
      const options = await registrationOptions()
      const credential = (await navigator.credentials.create({
        publicKey: {
          challenge: Uint8Array.from(atob(options.challenge.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0)),
          rp: { id: options.rpId, name: options.rpName },
          user: {
            id: Uint8Array.from(atob(options.userId.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0)),
            name: options.userName,
            displayName: options.userDisplayName,
          },
          pubKeyCredParams: [{ type: 'public-key', alg: -7 }],
          timeout: 60000,
          authenticatorSelection: {
            userVerification: options.userVerification as UserVerificationRequirement,
          },
          excludeCredentials: options.excludeCredentials.map((c) => ({
            type: 'public-key',
            id: Uint8Array.from(atob(c.id.replace(/-/g, '+').replace(/_/g, '/')), (ch) => ch.charCodeAt(0)),
          })),
        },
      })) as PublicKeyCredential | null
      if (!credential) throw new Error('Passkey cancelled')
      const attestation = credential.response as AuthenticatorAttestationResponse
      const publicKeyCose = btoa(String.fromCharCode(...new Uint8Array(attestation.attestationObject)))
      return registrationVerify({
        credentialId: credential.id,
        publicKeyCose,
        challenge: options.challenge,
        origin: window.location.origin,
        name: passkeyName || undefined,
      })
    },
    onSuccess: () => void mfaSettingsQuery.refetch(),
  })

  const generateRecoveryCodesMutation = useMutation({
    mutationFn: (regenerate: boolean) => generateRecoveryCodes(regenerate),
    onSuccess: (codes) => {
      setRecoveryCodes(codes.codes)
      setAckSavedCodes(false)
    },
  })

  return (
    <div className="space-y-6">
      <PageHeader title="Security & sessions" subtitle="Protect your account and manage active sessions." />
      <SectionCard title="Session controls" description="Sign out or revoke access on other devices.">
        <p className="mb-4 text-body-md text-on-surface-variant">
          Session tokens are never shown here. Use revoke actions to invalidate other devices.
        </p>
        <ul className="mb-4 space-y-2">
          <SessionRow deviceLabel="Current browser" lastActive="Now" current revokeDisabled />
        </ul>
        <p className="mb-3 text-label-md text-on-surface-variant">
          Per-session listing requires a sessions API. Use revoke all to sign out everywhere.
        </p>
        <div className="flex flex-wrap gap-2">
          <Button type="button" onClick={() => logoutMutation.mutate()} disabled={logoutMutation.isPending}>
            Log out
          </Button>
          <Button
            type="button"
            className="bg-error text-white hover:opacity-90"
            onClick={() => setConfirmRevokeAll(true)}
          >
            Revoke all sessions
          </Button>
        </div>
        {revokeMutation.isSuccess ? (
          <p className="mt-2 text-body-md text-on-surface-variant">
            Revoked {revokeMutation.data.revokedCount} session(s).
          </p>
        ) : null}
        {revokeMutation.isError ? <ErrorState error={revokeMutation.error} className="mt-3" /> : null}
      </SectionCard>
      {mfaUiEnabled ? (
        <SecurityMethodCard
          title="Passkeys & MFA"
          description="WebAuthn passkeys and recovery codes."
          enabled={Boolean(mfaSettingsQuery.data?.webauthnEnabled)}
        >
          <p className="text-body-md text-on-surface-variant">
            Credentials: {mfaSettingsQuery.data?.activeCredentialCount ?? 0} · Recovery codes remaining:{' '}
            {mfaSettingsQuery.data?.recoveryCodesRemaining ?? 0}
          </p>
          {!webAuthnSupported ? (
            <p className="mt-2 text-label-md text-amber-800">WebAuthn is not available in this browser context.</p>
          ) : null}
          <div className="mt-3 flex flex-wrap gap-2">
            <Input
              placeholder="Passkey label"
              value={passkeyName}
              onChange={(e) => setPasskeyName(e.target.value)}
              aria-label="Passkey label"
            />
            <Button
              type="button"
              disabled={!mfaSettingsQuery.data?.mfaEnabled || setupPasskeyMutation.isPending}
              onClick={() => setupPasskeyMutation.mutate()}
            >
              Set up passkey
            </Button>
            <Button
              type="button"
              disabled={!mfaSettingsQuery.data?.mfaEnabled}
              onClick={() =>
                generateRecoveryCodesMutation.mutate((mfaSettingsQuery.data?.recoveryCodesRemaining ?? 0) > 0)
              }
            >
              {(mfaSettingsQuery.data?.recoveryCodesRemaining ?? 0) > 0 ? 'Regenerate' : 'Generate'} recovery codes
            </Button>
          </div>
          {recoveryCodes ? (
            <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-label-md text-amber-900">
              <p className="font-medium">Recovery codes (shown once)</p>
              <p className="mt-1">Store these codes securely. They are not session tokens.</p>
              <ul className="mt-2 list-inside list-disc">
                {recoveryCodes.map((code) => (
                  <li key={code}>{code}</li>
                ))}
              </ul>
              <label className="mt-2 flex items-center gap-2">
                <input type="checkbox" checked={ackSavedCodes} onChange={(e) => setAckSavedCodes(e.target.checked)} />
                I saved these codes
              </label>
            </div>
          ) : null}
          {setupPasskeyMutation.isError ? <ErrorState error={setupPasskeyMutation.error} className="mt-3" /> : null}
        </SecurityMethodCard>
      ) : null}
      <ConfirmActionModal
        open={confirmRevokeAll}
        title="Revoke all sessions?"
        message="This signs you out on all devices including this one. You will need to sign in again."
        confirmLabel="Revoke all"
        tone="danger"
        loading={revokeMutation.isPending}
        onConfirm={() => revokeMutation.mutate()}
        onClose={() => setConfirmRevokeAll(false)}
      />
    </div>
  )
}
