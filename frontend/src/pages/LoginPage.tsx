import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { useState } from 'react'
import { authUserFromMeResponse, listSsoProviders, login, loginSchema, me } from '../features/auth/auth-api'
import { useAuthStore } from '../features/auth/auth-store'
import { setMfaSessionId } from '../features/auth/mfa-session'
import { ssoErrorMessage } from '../features/auth/sso-errors'
import {
  AuthShell,
  AuthLogo,
  AuthCard,
  AuthInput,
  AuthButton,
  SsoButton,
} from '../features/auth/components'
import { ErrorAlert } from '../shared/components/ErrorAlert'
import { isSsoEnabled } from '../shared/config/sso-feature-flags'
import { API_BASE_URL } from '../shared/api/api-client'
import { VerifiedIcon } from '../features/auth/components/AuthIcons'

function AuthDivider() {
  return (
    <div className="my-6 flex items-center">
      <span className="h-px flex-1 bg-outline-variant" />
      <span className="px-4 text-label-md text-on-surface-variant">or</span>
      <span className="h-px flex-1 bg-outline-variant" />
    </div>
  )
}

export function LoginPage() {
  const navigate = useNavigate()
  const setSession = useAuthStore((state) => state.setSession)
  const setUser = useAuthStore((state) => state.setUser)
  const [searchParams] = useSearchParams()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
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
        navigate('/mfa')
        return
      }
      setSession({
        accessToken: data.accessToken ?? null,
        refreshToken: data.refreshToken ?? null,
        user: data.user,
      })
      try {
        const profile = await me()
        setUser(authUserFromMeResponse(profile, data.user.status))
      } catch {
        // session may still be valid
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

  const startSso = (registrationId: string) => {
    const returnUrl = encodeURIComponent('/sso/callback?next=/app')
    window.location.assign(
      `${API_BASE_URL}/auth/sso/${registrationId}/authorize?returnUrl=${returnUrl}`,
    )
  }

  return (
    <AuthShell>
      <AuthLogo subtitle="Log in to your enterprise workspace" />
      <AuthCard>
        <form className="space-y-4" onSubmit={onSubmit} noValidate>
          <AuthInput
            label="Email"
            name="email"
            type="email"
            autoComplete="email"
            placeholder="name@company.com"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
          <AuthInput
            label="Password"
            name="password"
            type="password"
            autoComplete="current-password"
            placeholder="••••••••"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            hint={
              <Link to="/forgot-password" className="text-label-md font-medium text-primary hover:text-primary-container">
                Forgot password?
              </Link>
            }
          />
          {mutation.isError ? <ErrorAlert error={mutation.error} /> : null}
          {ssoErrorCode ? (
            <p className="rounded-lg border border-error-container bg-error-container/50 px-3 py-2 text-body-md text-error" role="alert">
              {ssoErrorMessage(ssoErrorCode)}
            </p>
          ) : null}
          <AuthButton type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? 'Signing in…' : 'Continue'}
          </AuthButton>
        </form>

        {ssoEnabled ? (
          <>
            <AuthDivider />
            {ssoQuery.data?.providers?.length ? (
              <div className="space-y-2">
                {ssoQuery.data.providers.map((provider) => (
                  <SsoButton
                    key={provider.registrationId}
                    label={`Continue with ${provider.label}`}
                    onClick={() => startSso(provider.registrationId)}
                  />
                ))}
              </div>
            ) : (
              <SsoButton disabled label="SSO not configured" />
            )}
            {ssoQuery.isError ? <ErrorAlert error={ssoQuery.error} /> : null}
          </>
        ) : (
          <p className="mt-4 text-center text-label-md text-on-surface-variant">SSO is not enabled in this environment.</p>
        )}

        <p className="mt-6 text-center text-body-md text-on-surface-variant">
          Don&apos;t have an account?{' '}
          <Link to="/register" className="font-medium text-primary hover:text-primary-container">
            Create an account
          </Link>
        </p>
      </AuthCard>
      <footer className="mt-6 flex items-center justify-center gap-2 text-on-surface-variant opacity-80">
        <VerifiedIcon className="h-4 w-4" />
        <span className="text-label-md">Secure, encrypted, and compliant.</span>
      </footer>
    </AuthShell>
  )
}
