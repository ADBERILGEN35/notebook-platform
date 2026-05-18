import { useMutation } from '@tanstack/react-query'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { authUserFromMeResponse, me } from '../features/auth/auth-api'
import type { AuthResponse } from '../shared/types/api'
import {
  authenticationOptions,
  authenticationVerify,
  verifyRecoveryCode,
} from '../features/auth/mfa-api'
import { useAuthStore } from '../features/auth/auth-store'
import { clearMfaSessionId, getMfaSessionId } from '../features/auth/mfa-session'
import {
  AuthShell,
  AuthLogo,
  AuthCard,
  AuthInput,
  AuthButton,
  MfaCodeInput,
} from '../features/auth/components'
import { ErrorAlert } from '../shared/components/ErrorAlert'

export function MfaAuthenticationPage() {
  const navigate = useNavigate()
  const mfaSessionId = getMfaSessionId()
  const setSession = useAuthStore((state) => state.setSession)
  const setUser = useAuthStore((state) => state.setUser)
  const [code, setCode] = useState('')
  const [recoveryCode, setRecoveryCode] = useState('')
  const [showRecovery, setShowRecovery] = useState(false)

  if (!mfaSessionId) {
    return <Navigate to="/login" replace />
  }

  const finishSession = async (data: AuthResponse) => {
    if (data.user) {
      setSession({
        accessToken: data.accessToken ?? null,
        refreshToken: data.refreshToken ?? null,
        user: data.user,
      })
    }
    const profile = await me()
    setUser(authUserFromMeResponse(profile, data.user?.status))
    clearMfaSessionId()
    navigate('/app')
  }

  const passkeyMutation = useMutation({
    mutationFn: async () => {
      const options = await authenticationOptions(mfaSessionId)
      const credential = (await navigator.credentials.get({
        publicKey: {
          challenge: Uint8Array.from(atob(options.challenge.replace(/-/g, '+').replace(/_/g, '/')), (c) =>
            c.charCodeAt(0),
          ),
          rpId: options.rpId,
          allowCredentials: options.allowCredentialIds.map((id) => ({
            id: Uint8Array.from(atob(id.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0)),
            type: 'public-key' as const,
          })),
          userVerification: options.userVerification as UserVerificationRequirement,
          timeout: 60000,
        },
      })) as PublicKeyCredential | null
      if (!credential) throw new Error('Passkey cancelled')
      return authenticationVerify({
        mfaSessionId,
        credentialId: credential.id,
        challenge: options.challenge,
        origin: window.location.origin,
      })
    },
    onSuccess: (data) => void finishSession(data as AuthResponse),
  })

  const recoveryMutation = useMutation({
    mutationFn: () => verifyRecoveryCode({ mfaSessionId, recoveryCode }),
    onSuccess: (data) => void finishSession(data as AuthResponse),
  })

  return (
    <AuthShell>
      <AuthLogo icon="shield" subtitle="Multi-factor authentication" />
      <AuthCard>
        <div className="mb-6 text-center">
          <h1 className="font-display text-headline-md text-on-surface">Verify your identity</h1>
          <p className="mt-2 text-body-md text-on-surface-variant">
            Use your passkey, recovery code, or enter a one-time code from your authenticator app.
          </p>
        </div>

        {!showRecovery ? (
          <>
            <MfaCodeInput value={code} onChange={setCode} disabled={passkeyMutation.isPending} />
            <p className="mt-2 text-center text-label-md text-on-surface-variant">
              TOTP verification uses passkey or recovery in this release.
            </p>
            <AuthButton
              type="button"
              className="mt-4"
              disabled={code.length < 6 || passkeyMutation.isPending}
              onClick={() => passkeyMutation.mutate()}
            >
              Verify with passkey
            </AuthButton>
            <AuthButton type="button" variant="ghost" fullWidth className="mt-2" onClick={() => setShowRecovery(true)}>
              Try another way
            </AuthButton>
          </>
        ) : (
          <>
            <AuthInput
              label="Recovery code"
              name="recovery"
              placeholder="XXXX-XXXX-XXXX"
              value={recoveryCode}
              onChange={(event) => setRecoveryCode(event.target.value)}
              autoComplete="off"
            />
            <AuthButton
              type="button"
              className="mt-4"
              disabled={!recoveryCode.trim() || recoveryMutation.isPending}
              onClick={() => recoveryMutation.mutate()}
            >
              Use recovery code
            </AuthButton>
            <AuthButton type="button" variant="ghost" fullWidth className="mt-2" onClick={() => setShowRecovery(false)}>
              Back to passkey
            </AuthButton>
          </>
        )}

        {passkeyMutation.isError ? (
          <div className="mt-4">
            <ErrorAlert error={passkeyMutation.error} />
          </div>
        ) : null}
        {recoveryMutation.isError ? (
          <div className="mt-4">
            <ErrorAlert error={recoveryMutation.error} />
          </div>
        ) : null}

        <p className="mt-6 text-center text-body-md text-on-surface-variant">
          <Link to="/login" className="text-primary hover:underline" onClick={() => clearMfaSessionId()}>
            Cancel and return to login
          </Link>
        </p>
      </AuthCard>
    </AuthShell>
  )
}
