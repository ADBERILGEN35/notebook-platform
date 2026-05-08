import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate, Link, useSearchParams } from 'react-router-dom'
import { useState } from 'react'
import { listSsoProviders, login, loginSchema, me } from '../features/auth/auth-api'
import { authenticationOptions, authenticationVerify, verifyRecoveryCode } from '../features/auth/mfa-api'
import { useAuthStore } from '../features/auth/auth-store'
import { Card } from '../shared/components/Card'
import { Input } from '../shared/components/Input'
import { Button } from '../shared/components/Button'
import { ErrorAlert } from '../shared/components/ErrorAlert'
import { isSsoEnabled } from '../shared/config/sso-feature-flags'
import { API_BASE_URL } from '../shared/api/api-client'

export function LoginPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const setSession = useAuthStore((state) => state.setSession)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [mfaSessionId, setMfaSessionId] = useState<string | null>(null)
  const [recoveryCode, setRecoveryCode] = useState('')

  const setUser = useAuthStore((state) => state.setUser)
  const ssoEnabled = isSsoEnabled()
  const ssoErrorCode = searchParams.get('error')
  const ssoQuery = useQuery({
    queryKey: ['sso-providers'],
    queryFn: listSsoProviders,
    enabled: ssoEnabled,
  })

  const mutation = useMutation({
    mutationFn: login,
    onSuccess: async (data) => {
      if (data.mfaRequired && data.mfaSessionId) {
        setMfaSessionId(data.mfaSessionId)
        return
      }
      setSession({
        accessToken: data.accessToken ?? null,
        refreshToken: data.refreshToken ?? null,
        user: data.user,
      })
      try {
        const m = await me()
        setUser({
          id: m.userId,
          email: m.email,
          name: m.name,
          avatarUrl: m.avatarUrl ?? null,
          status: data.user.status,
          roles: m.roles ?? [],
        })
      } catch {
        // session may still be valid; admin role gating falls back to feature flags
      }
      navigate('/app')
    },
  })

  const onSubmit = (event: React.FormEvent) => {
    event.preventDefault()
    const parsed = loginSchema.safeParse({ email, password })
    if (!parsed.success) return
    mutation.mutate(parsed.data)
  }

  const mfaPasskeyMutation = useMutation({
    mutationFn: async () => {
      if (!mfaSessionId) throw new Error('MFA session missing')
      const options = await authenticationOptions(mfaSessionId)
      const credential = (await navigator.credentials.get({
        publicKey: {
          challenge: Uint8Array.from(atob(options.challenge.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0)),
          rpId: options.rpId,
          allowCredentials: options.allowCredentialIds.map((id) => ({
            id: Uint8Array.from(atob(id.replace(/-/g, '+').replace(/_/g, '/')), (c) => c.charCodeAt(0)),
            type: 'public-key',
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
    onSuccess: async (data: any) => {
      if (data?.user) {
        setSession({
          accessToken: data.accessToken ?? null,
          refreshToken: data.refreshToken ?? null,
          user: data.user,
        })
      }
      const m = await me()
      setUser({ id: m.userId, email: m.email, name: m.name, avatarUrl: m.avatarUrl ?? null, roles: m.roles ?? [] })
      navigate('/app')
    },
  })

  const mfaRecoveryMutation = useMutation({
    mutationFn: async () => {
      if (!mfaSessionId) throw new Error('MFA session missing')
      return verifyRecoveryCode({ mfaSessionId, recoveryCode })
    },
    onSuccess: async (data: any) => {
      if (data?.user) {
        setSession({
          accessToken: data.accessToken ?? null,
          refreshToken: data.refreshToken ?? null,
          user: data.user,
        })
      }
      const m = await me()
      setUser({ id: m.userId, email: m.email, name: m.name, avatarUrl: m.avatarUrl ?? null, roles: m.roles ?? [] })
      navigate('/app')
    },
  })

  return (
    <div className="grid min-h-screen place-items-center p-4">
      <Card>
        <h1 className="mb-4 text-xl font-semibold">Login</h1>
        {mfaSessionId ? (
          <div className="space-y-3">
            <p className="text-sm text-slate-600">MFA required. Use passkey or recovery code.</p>
            <Button type="button" className="w-full bg-primary-600 text-white hover:bg-primary-700" onClick={() => mfaPasskeyMutation.mutate()}>
              Use passkey
            </Button>
            <Input
              placeholder="Recovery code (XXXX-XXXX-XXXX)"
              value={recoveryCode}
              onChange={(event) => setRecoveryCode(event.target.value)}
            />
            <Button type="button" className="w-full" onClick={() => mfaRecoveryMutation.mutate()}>
              Use recovery code
            </Button>
            {mfaPasskeyMutation.isError ? <ErrorAlert error={mfaPasskeyMutation.error} /> : null}
            {mfaRecoveryMutation.isError ? <ErrorAlert error={mfaRecoveryMutation.error} /> : null}
          </div>
        ) : (
          <form className="space-y-3" onSubmit={onSubmit}>
          <Input placeholder="Email" value={email} onChange={(event) => setEmail(event.target.value)} />
          <Input
            placeholder="Password"
            type="password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          {mutation.isError ? <ErrorAlert error={mutation.error} /> : null}
          <Button className="w-full bg-primary-600 text-white hover:bg-primary-700" type="submit">
            Sign in
          </Button>
          </form>
        )}
        {ssoEnabled ? (
          <div className="mt-4 border-t border-slate-200 pt-3">
            <p className="mb-2 text-sm font-medium text-slate-800">Continue with SSO</p>
            {ssoQuery.data?.providers?.map((provider) => (
              <Button
                key={provider.registrationId}
                type="button"
                className="mb-2 w-full"
                onClick={() => {
                  const returnUrl = encodeURIComponent('/app')
                  window.location.assign(
                    `${API_BASE_URL}/auth/sso/${provider.registrationId}/authorize?returnUrl=${returnUrl}`,
                  )
                }}
              >
                {provider.label}
              </Button>
            ))}
            {ssoQuery.isError ? <ErrorAlert error={ssoQuery.error} /> : null}
          </div>
        ) : null}
        {ssoErrorCode ? (
          <p className="mt-3 rounded border border-rose-200 bg-rose-50 px-2 py-1 text-xs text-rose-700">
            SSO login failed: {ssoErrorCode}
          </p>
        ) : null}
        <p className="mt-3 text-sm text-slate-600">
          No account? <Link to="/signup">Create one</Link>
        </p>
      </Card>
    </div>
  )
}

